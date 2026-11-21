package com.modularmc.registrate.localization.resolution

import com.modularmc.registrate.internal.util.RegistrateLogger
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.MinecraftLocale.EN_UD
import com.modularmc.registrate.localization.MinecraftLocale.EN_US
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.localization.terminology.Terminology
import com.modularmc.registrate.localization.terminology.Terminology.GlossaryTerm

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap

import java.util.TreeMap

/** Resolves the declarations for one DataGen output before the language provider writes any files. */
class TranslationRegistry {
	private val selections = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()

	@JvmRecord
	data class LanguageFile(val namespace: String, val locale: MinecraftLocale)

	private data class EntryKey(val key: TranslationKey, val locale: MinecraftLocale) {
		override fun toString(): String = "${key.namespace}/${key.value} (${locale.code()})"
	}

	private class Candidate(val text: String, val source: TranslationSource, val original: Selection?, val declaration: LocalizationDeclaration? = null, val terms: Terminology = Terminology.EMPTY)

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
		val original = selections.add(EntryKey(key, locale), Candidate(declaration.source, source, null, declaration, terminology))
		for ((target, text) in declaration.translations) {
			selections.add(EntryKey(key, target), Candidate(text, source, original))
		}
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
		val glossary = collectGlossary()
		val files = Object2ObjectLinkedOpenHashMap<LanguageFile, MutableMap<String, String>>()
		writeSelections(selections, glossary, files)
		writeSelections(glossary, glossary, files)
		selections.object2ObjectEntrySet().fastForEach { it.value.verifyPlaceholders(it.key) }
		glossary.object2ObjectEntrySet().fastForEach { it.value.verifyPlaceholders(it.key) }
		return files
	}

	private fun collectGlossary(): Object2ObjectLinkedOpenHashMap<EntryKey, Selection> {
		val glossary = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()
		selections.object2ObjectEntrySet().fastForEach { entry ->
			val key = entry.key
			val selection = entry.value
			selection.verifyText(key)
			selection.forEachWinner { candidate ->
				val declaration = candidate.declaration ?: return@forEachWinner
				val term = candidate.terms.resolveTerm(key.key, declaration, candidate.source) ?: return@forEachWinner
				val source = TranslationSource(Kind.GLOSSARY, "${candidate.source.location} (glossary ${term.id})")
				for ((locale, text) in term.preferredTranslations) {
					if (locale == key.locale) continue
					val target = key.copy(locale = locale)
					val explicit = selections[target]
					if (explicit == null) {
						glossary.add(target, Candidate(text, source, selection))
					} else {
						checkGlossaryText(target, term, text, explicit.winner)
					}
				}
			}
		}
		return glossary
	}

	private fun checkGlossaryText(key: EntryKey, term: GlossaryTerm, preferred: String, candidate: Candidate) {
		val text = candidate.text
		val forbidden = term.forbiddenTranslations[key.locale]?.any { it.equals(text, ignoreCase = true) } == true
		val accepted = preferred.equals(text, ignoreCase = true) || term.acceptedTranslations[key.locale]?.any { it.equals(text, ignoreCase = true) } == true
		if (forbidden || !accepted) {
			RegistrateLogger.datagen().warn("Glossary term {} for {} at {} prefers '{}', found '{}'", term.id, key, candidate.source.location, preferred, text)
		}
	}

	private fun writeSelections(
		entries: Object2ObjectLinkedOpenHashMap<EntryKey, Selection>,
		glossary: Object2ObjectLinkedOpenHashMap<EntryKey, Selection>,
		files: MutableMap<LanguageFile, MutableMap<String, String>>,
	) {
		val iterator = entries.object2ObjectEntrySet().fastIterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			val key = entry.key
			val selection = entry.value
			selection.verifyText(key)

			val winner = selection.winner
			val variantKey = if (key.locale == EN_US) key.copy(locale = EN_UD) else null
			val invert = variantKey != null && !selections.containsKey(variantKey) && !glossary.containsKey(variantKey)
			val parsed = try {
				MinecraftTextFormat.parse(winner.text, invert)
			} catch (error: IllegalArgumentException) {
				throw IllegalArgumentException("Invalid Minecraft placeholder for $key at ${winner.source.location}", error)
			}
			selection.signature = parsed.signature
			files.computeIfAbsent(LanguageFile(key.key.namespace, key.locale)) { TreeMap() }[key.key.value] = winner.text
			if (variantKey != null && parsed.variant != null) {
				files.computeIfAbsent(LanguageFile(key.key.namespace, EN_UD)) { TreeMap() }[key.key.value] = parsed.variant
			}
		}
	}
}
