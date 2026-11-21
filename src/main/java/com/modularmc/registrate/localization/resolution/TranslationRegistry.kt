package com.modularmc.registrate.localization.resolution

import com.modularmc.registrate.internal.util.RegistrateLogger.datagen
import com.modularmc.registrate.localization.ImportedTranslation
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.SourceRevision
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.localization.TranslationStatus.STALE
import com.modularmc.registrate.localization.locale.LocaleDerivation
import com.modularmc.registrate.localization.locale.LocaleDerivationGraph
import com.modularmc.registrate.localization.terminology.Terminology
import com.modularmc.registrate.localization.terminology.Terminology.GlossaryTerm

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectMap
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap

import java.util.TreeMap
import java.util.function.Consumer

/** Resolves the declarations for one DataGen output before the language provider writes any files. */
class TranslationRegistry {
	private val selections = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()
	private val imports = ObjectArrayList<PendingImport>()
	private val declarations = Reference2ObjectOpenHashMap<Terminology, Object2ObjectOpenHashMap<EntryKey, Candidate>>()
	private val importRevisions = Reference2ObjectOpenHashMap<Candidate, SourceRevision>()
	private val derivations = Reference2ObjectOpenHashMap<Terminology, LocaleDerivationGraph>()

	@JvmRecord
	data class LanguageFile(val namespace: String, val locale: MinecraftLocale)

	private data class EntryKey(val key: TranslationKey, val locale: MinecraftLocale) {
		override fun toString(): String = "${key.namespace}/${key.value} (${locale.code()})"
	}

	private class Declaration(val localization: LocalizationDeclaration, val terminology: Terminology)

	private class PendingImport(val sourceLocale: MinecraftLocale, val translation: ImportedTranslation, val terminology: Terminology)

	private class Candidate(val text: String, val source: TranslationSource, val declaration: Declaration, val original: Selection? = null, val importPriority: Int = 0) {
		fun comparePriority(other: Candidate): Int {
			val sourcePriority = source.kind.priority.compareTo(other.source.kind.priority)
			return if (sourcePriority != 0) sourcePriority else importPriority.compareTo(other.importPriority)
		}
	}

	private class Selection(private val key: EntryKey, var winner: Candidate) {
		private var ties: MutableList<Candidate>? = null
		private var signature: Int2IntMap? = null

		fun add(candidate: Candidate) {
			val precedence = candidate.comparePriority(winner)
			if (precedence > 0) {
				winner = candidate
				ties = null
				signature = null
			} else if (precedence == 0) {
				val candidates = ties ?: ArrayList<Candidate>(1).also { ties = it }
				candidates.add(candidate)
			}
		}

		fun verifyText() {
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

		fun verifyPlaceholders() {
			val expected = placeholderSignature()
			forEachWinner { candidate ->
				val original = candidate.original ?: return@forEachWinner
				require(original.placeholderSignature() == expected) { "Minecraft placeholder mismatch for $key at ${candidate.source.location}" }
			}
		}

		private fun placeholderSignature(): Int2IntMap {
			signature?.let { return it }
			return try {
				MinecraftTextFormat.signature(winner.text).also { signature = it }
			} catch (error: IllegalArgumentException) {
				throw IllegalArgumentException("Invalid Minecraft placeholder for $key at ${winner.source.location}", error)
			}
		}
	}

	@JvmOverloads
	fun add(key: TranslationKey, locale: MinecraftLocale, declaration: LocalizationDeclaration, source: TranslationSource, terminology: Terminology = Terminology.EMPTY) {
		val context = Declaration(declaration, terminology)
		val sourceKey = EntryKey(key, locale)
		val sourceCandidate = Candidate(declaration.source, source, context)
		val original = selections.add(sourceKey, sourceCandidate)
		val ownedDeclarations = declarations.computeIfAbsent(terminology) { Object2ObjectOpenHashMap() }
		val ownedDeclaration = ownedDeclarations[sourceKey]
		if (ownedDeclaration == null || sourceCandidate.comparePriority(ownedDeclaration) > 0) ownedDeclarations[sourceKey] = sourceCandidate
		for ((target, text) in declaration.translations) {
			selections.add(EntryKey(key, target), Candidate(text, source, context, original))
		}
	}

	internal fun addImported(sourceLocale: MinecraftLocale, translation: ImportedTranslation, terminology: Terminology) {
		val key = EntryKey(translation.key, sourceLocale)
		require(declarations[terminology]?.containsKey(key) == true) {
			"No source declaration for import $key at ${translation.location}"
		}
		val target = key.copy(locale = translation.locale)
		require(target != key) { "Imported translation $target at ${translation.location} uses the source locale" }
		imports.add(PendingImport(sourceLocale, translation, terminology))
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
		return Selection(key, candidate).also { put(key, it) }
	}

	/** Resolves language entries and, when requested, reports each matched import with its current status. */
	@JvmOverloads
	fun resolve(reportImport: Consumer<ImportedTranslation>? = null): Map<LanguageFile, Map<String, String>> {
		val imported = resolveImports(reportImport)
		val files = Object2ObjectLinkedOpenHashMap<LanguageFile, MutableMap<String, String>>()
		forEachSelection(imported) { key, selection -> collectLanguageText(key, selection, files) }

		val glossaryTerms = Reference2ObjectOpenHashMap<Declaration, GlossaryTerm>()
		val glossary = collectGlossary(imported, glossaryTerms)
		glossary.object2ObjectEntrySet().fastForEach { collectLanguageText(it.key, it.value, files) }
		val variants = collectDerivations(imported, glossary, glossaryTerms)
		variants.object2ObjectEntrySet().fastForEach { collectLanguageText(it.key, it.value, files) }
		return files
	}

	private fun resolveImports(reportImport: Consumer<ImportedTranslation>?): Object2ObjectMap<EntryKey, Selection> {
		if (imports.isEmpty) return Object2ObjectMaps.emptyMap()

		val selected = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()
		val logger = datagen()
		for (pending in imports) {
			val key = EntryKey(pending.translation.key, pending.sourceLocale)
			val declarationCandidate = declarations[pending.terminology]!![key]!!
			val original = selections.getValue(key)
			val declaration = declarationCandidate.declaration
			val revision = importRevisions.getOrPut(declarationCandidate) { SourceRevision.of(declaration.localization.source) }
			val translation = pending.translation.forRevision(revision)
			reportImport?.accept(translation)

			val target = key.copy(locale = translation.locale)
			if (translation.status == STALE) {
				logger.warn("Stale translation for {} at {} (reviewed revision {}, current revision {})", target, translation.location, translation.sourceRevision.value, revision.value)
				if (!translation.locked) continue
			}
			val existing = selections[target]
			if (existing != null && existing.winner.source.kind.priority > Kind.IMPORTED.priority) continue
			val source = TranslationSource(Kind.IMPORTED, translation.location)
			selected.add(target, Candidate(translation.text, source, declaration, original, translation.status.priority(translation.locked)))
		}
		return selected
	}

	private inline fun forEachSelection(imported: Object2ObjectMap<EntryKey, Selection>, action: (EntryKey, Selection) -> Unit) {
		val entries = selections.object2ObjectEntrySet().fastIterator()
		while (entries.hasNext()) {
			val entry = entries.next()
			action(entry.key, imported[entry.key] ?: entry.value)
		}
		val translations = Object2ObjectMaps.fastIterator(imported)
		while (translations.hasNext()) {
			val entry = translations.next()
			if (!selections.containsKey(entry.key)) action(entry.key, entry.value)
		}
	}

	private fun collectGlossary(imported: Object2ObjectMap<EntryKey, Selection>, terms: MutableMap<Declaration, GlossaryTerm>): Object2ObjectLinkedOpenHashMap<EntryKey, Selection> {
		val glossary = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()
		forEachSelection(imported) { key, selection ->
			selection.forEachWinner { candidate ->
				if (candidate.original != null) return@forEachWinner
				val declaration = candidate.declaration
				val term = declaration.terminology.resolveTerm(key.key, declaration.localization, candidate.source) ?: return@forEachWinner
				terms[declaration] = term
				for (synonym in declaration.terminology.synonyms(term)) {
					val source = TranslationSource(Kind.GLOSSARY, "${candidate.source.location} (glossary ${synonym.id})")
					for ((locale, text) in synonym.preferredTranslations) {
						if (locale == key.locale) continue
						val target = key.copy(locale = locale)
						val explicit = imported[target] ?: selections[target]
						if (explicit == null) {
							glossary.add(target, Candidate(text, source, declaration, selection))
						} else {
							checkGlossaryText(target, synonym, text, explicit.winner)
						}
					}
				}
			}
		}
		return glossary
	}

	private fun collectDerivations(
		imported: Object2ObjectMap<EntryKey, Selection>,
		glossary: Object2ObjectLinkedOpenHashMap<EntryKey, Selection>,
		terms: Map<Declaration, GlossaryTerm>,
	): Object2ObjectLinkedOpenHashMap<EntryKey, Selection> {
		val variants = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()

		fun expand(key: EntryKey, candidate: Candidate, original: Selection, graph: LocaleDerivationGraph, symbols: Collection<String>) {
			for (rule in graph.from(key.locale)) {
				val target = key.copy(locale = rule.output)
				if (selections.containsKey(target) || imported.containsKey(target) || glossary.containsKey(target)) continue
				val kind = if (candidate.source.kind == Kind.FALLBACK) Kind.FALLBACK else rule.kind
				val source = TranslationSource(kind, "${candidate.source.location} (${key.locale.code()} -> ${target.locale.code()})")
				val declaration = candidate.declaration
				val converted = rule.derive(candidate.text, symbols, key.key, declaration.localization)
				val variant = Candidate(converted, source, declaration, original)
				variants.add(target, variant)
				expand(target, variant, original, graph, symbols)
			}
		}

		fun expandFrom(key: EntryKey, selection: Selection) {
			selection.forEachWinner { candidate ->
				val declaration = candidate.declaration
				val graph = derivations[declaration.terminology] ?: LocaleDerivationGraph.DEFAULT
				if (graph.from(key.locale).isEmpty()) return@forEachWinner
				val symbols = unitSymbols(declaration, terms[declaration])
				expand(key, candidate, selection, graph, symbols)
			}
		}
		forEachSelection(imported, ::expandFrom)
		glossary.object2ObjectEntrySet().fastForEach { expandFrom(it.key, it.value) }
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

	private fun checkGlossaryText(key: EntryKey, term: GlossaryTerm, preferred: String, candidate: Candidate) {
		val text = candidate.text
		val forbidden = term.forbiddenTranslations[key.locale]?.any { it.equals(text, ignoreCase = true) } == true
		val accepted = preferred.equals(text, ignoreCase = true) || term.acceptedTranslations[key.locale]?.any { it.equals(text, ignoreCase = true) } == true
		if (forbidden || !accepted) {
			datagen().warn("Glossary term {} for {} at {} prefers '{}', found '{}'", term.id, key, candidate.source.location, preferred, text)
		}
	}

	private fun collectLanguageText(key: EntryKey, selection: Selection, files: MutableMap<LanguageFile, MutableMap<String, String>>) {
		selection.verifyText()
		selection.verifyPlaceholders()
		files.computeIfAbsent(LanguageFile(key.key.namespace, key.locale)) { TreeMap() }[key.key.value] = selection.winner.text
	}
}
