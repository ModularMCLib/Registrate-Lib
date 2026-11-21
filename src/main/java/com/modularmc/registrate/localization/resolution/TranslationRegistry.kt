package com.modularmc.registrate.localization.resolution

import com.modularmc.registrate.internal.util.RegistrateLogger
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.localization.locale.LocaleDerivation
import com.modularmc.registrate.localization.locale.LocaleDerivationGraph
import com.modularmc.registrate.localization.terminology.Terminology
import com.modularmc.registrate.localization.terminology.Terminology.GlossaryTerm

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap

import java.util.TreeMap

/** Resolves the declarations for one DataGen output before the language provider writes any files. */
class TranslationRegistry {
	private val selections = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()
	private val derivations = Reference2ObjectOpenHashMap<Terminology, LocaleDerivationGraph>()

	@JvmRecord
	data class LanguageFile(val namespace: String, val locale: MinecraftLocale)

	private data class EntryKey(val key: TranslationKey, val locale: MinecraftLocale) {
		override fun toString(): String = "${key.namespace}/${key.value} (${locale.code()})"
	}

	private class Declaration(val localization: LocalizationDeclaration, val terminology: Terminology)

	private class Candidate(val text: String, val source: TranslationSource, val declaration: Declaration, val original: Selection? = null)

	private class Selection(var winner: Candidate) {
		private var ties: MutableList<Candidate>? = null
		lateinit var signature: Int2IntMap

		fun add(candidate: Candidate) {
			val precedence = candidate.source.kind.compareTo(winner.source.kind)
			if (precedence > 0) {
				winner = candidate
				ties = null
			} else if (precedence == 0) {
				val candidates = ties ?: ArrayList<Candidate>(1).also { ties = it }
				candidates.add(candidate)
			}
		}

		fun verifyText(key: EntryKey) {
			ties?.forEach { candidate ->
				require(candidate.text == winner.text) {
					"Conflicting translations for $key from ${winner.source.location} and ${candidate.source.location}"
				}
			}
		}

		fun forEachWinner(action: (Candidate) -> Unit) {
			action(winner)
			ties?.forEach(action)
		}

		fun verifyPlaceholders(key: EntryKey) {
			verifyPlaceholders(key, winner)
			ties?.forEach { verifyPlaceholders(key, it) }
		}

		private fun verifyPlaceholders(key: EntryKey, candidate: Candidate) {
			candidate.original?.let { original ->
				require(original.signature == signature) { "Minecraft placeholder mismatch for $key at ${candidate.source.location}" }
			}
		}
	}

	@JvmOverloads
	fun add(key: TranslationKey, locale: MinecraftLocale, declaration: LocalizationDeclaration, source: TranslationSource, terminology: Terminology = Terminology.EMPTY) {
		val context = Declaration(declaration, terminology)
		val original = selections.add(EntryKey(key, locale), Candidate(declaration.source, source, context))
		for ((target, text) in declaration.translations) {
			selections.add(EntryKey(key, target), Candidate(text, source, context, original))
		}
	}

	internal fun configureDerivations(terminology: Terminology, modId: String, rules: Collection<LocaleDerivation>) {
		derivations[terminology] = LocaleDerivationGraph(modId, rules)
	}

	private fun Object2ObjectLinkedOpenHashMap<EntryKey, Selection>.add(key: EntryKey, candidate: Candidate): Selection {
		val selection = this[key]
		if (selection != null) {
			selection.add(candidate)
			return selection
		}
		return Selection(candidate).also { put(key, it) }
	}

	fun resolve(): Map<LanguageFile, Map<String, String>> {
		val glossaryTerms = Reference2ObjectOpenHashMap<Declaration, GlossaryTerm>()
		val glossary = collectGlossary(glossaryTerms)
		val files = Object2ObjectLinkedOpenHashMap<LanguageFile, MutableMap<String, String>>()
		writeSelections(selections, files)
		writeSelections(glossary, files)
		val variants = collectDerivations(glossary, glossaryTerms)
		writeSelections(variants, files)
		verifyPlaceholders(selections)
		verifyPlaceholders(glossary)
		verifyPlaceholders(variants)
		return files
	}

	private fun collectGlossary(terms: MutableMap<Declaration, GlossaryTerm>): Object2ObjectLinkedOpenHashMap<EntryKey, Selection> {
		val glossary = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()
		selections.object2ObjectEntrySet().fastForEach { entry ->
			val key = entry.key
			val selection = entry.value
			selection.verifyText(key)
			selection.forEachWinner { candidate ->
				if (candidate.original != null) return@forEachWinner
				val declaration = candidate.declaration
				val term = declaration.terminology.resolveTerm(key.key, declaration.localization, candidate.source) ?: return@forEachWinner
				terms[declaration] = term
				val source = TranslationSource(Kind.GLOSSARY, "${candidate.source.location} (glossary ${term.id})")
				for ((locale, text) in term.preferredTranslations) {
					if (locale == key.locale) continue
					val target = key.copy(locale = locale)
					val explicit = selections[target]
					if (explicit == null) {
						glossary.add(target, Candidate(text, source, declaration, selection))
					} else {
						checkGlossaryText(target, term, text, explicit.winner)
					}
				}
			}
		}
		return glossary
	}

	private fun collectDerivations(glossary: Object2ObjectLinkedOpenHashMap<EntryKey, Selection>, terms: Map<Declaration, GlossaryTerm>): Object2ObjectLinkedOpenHashMap<EntryKey, Selection> {
		val variants = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()

		fun expand(key: EntryKey, candidate: Candidate, original: Selection, graph: LocaleDerivationGraph, symbols: Collection<String>) {
			for (rule in graph.from(key.locale)) {
				val target = key.copy(locale = rule.output)
				if (selections.containsKey(target) || glossary.containsKey(target)) continue
				val kind = if (candidate.source.kind == Kind.FALLBACK) Kind.FALLBACK else rule.kind
				val source = TranslationSource(kind, "${candidate.source.location} (${key.locale.code()} -> ${target.locale.code()})")
				val declaration = candidate.declaration
				val converted = rule.derive(candidate.text, symbols, key.key, declaration.localization)
				val variant = Candidate(converted, source, declaration, original)
				variants.add(target, variant)
				expand(target, variant, original, graph, symbols)
			}
		}

		fun expandFrom(entries: Object2ObjectLinkedOpenHashMap<EntryKey, Selection>) {
			entries.object2ObjectEntrySet().fastForEach { entry ->
				entry.value.forEachWinner { candidate ->
					val declaration = candidate.declaration
					val graph = derivations[declaration.terminology] ?: LocaleDerivationGraph.DEFAULT
					if (graph.from(entry.key.locale).isEmpty()) return@forEachWinner
					val symbols = unitSymbols(declaration, terms[declaration])
					expand(entry.key, candidate, entry.value, graph, symbols)
				}
			}
		}
		expandFrom(selections)
		expandFrom(glossary)
		return variants
	}

	private fun unitSymbols(declaration: Declaration, term: GlossaryTerm?): Collection<String> {
		val ids = declaration.localization.unitIds
		val termIds = term?.unitIds.orEmpty()
		if (ids.isEmpty() && termIds.isEmpty()) return emptyList()
		val unitSymbols = ObjectOpenHashSet<String>(ids.size + termIds.size)
		for (id in ids) unitSymbols.add(declaration.terminology.unitSymbol(id))
		for (id in termIds) unitSymbols.add(declaration.terminology.unitSymbol(id))
		return unitSymbols
	}

	private fun verifyPlaceholders(entries: Object2ObjectLinkedOpenHashMap<EntryKey, Selection>) {
		entries.object2ObjectEntrySet().fastForEach { it.value.verifyPlaceholders(it.key) }
	}

	private fun checkGlossaryText(key: EntryKey, term: GlossaryTerm, preferred: String, candidate: Candidate) {
		val text = candidate.text
		val forbidden = term.forbiddenTranslations[key.locale]?.any { it.equals(text, ignoreCase = true) } == true
		val accepted = preferred.equals(text, ignoreCase = true) || term.acceptedTranslations[key.locale]?.any { it.equals(text, ignoreCase = true) } == true
		if (forbidden || !accepted) {
			RegistrateLogger.datagen().warn("Glossary term {} for {} at {} prefers '{}', found '{}'", term.id, key, candidate.source.location, preferred, text)
		}
	}

	private fun writeSelections(entries: Object2ObjectLinkedOpenHashMap<EntryKey, Selection>, files: MutableMap<LanguageFile, MutableMap<String, String>>) {
		val iterator = entries.object2ObjectEntrySet().fastIterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			val key = entry.key
			val selection = entry.value
			selection.verifyText(key)

			val winner = selection.winner
			selection.signature = try {
				MinecraftTextFormat.signature(winner.text)
			} catch (error: IllegalArgumentException) {
				throw IllegalArgumentException("Invalid Minecraft placeholder for $key at ${winner.source.location}", error)
			}
			files.computeIfAbsent(LanguageFile(key.key.namespace, key.locale)) { TreeMap() }[key.key.value] = winner.text
		}
	}
}
