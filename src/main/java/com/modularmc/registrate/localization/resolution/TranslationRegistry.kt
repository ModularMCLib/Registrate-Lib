package com.modularmc.registrate.localization.resolution

import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.MinecraftLocale.EN_UD
import com.modularmc.registrate.localization.MinecraftLocale.EN_US
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource

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

	private class Candidate(val text: String, val source: TranslationSource, val original: Selection?)

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

	fun add(key: TranslationKey, locale: MinecraftLocale, declaration: LocalizationDeclaration, source: TranslationSource) {
		val original = add(EntryKey(key, locale), Candidate(declaration.source(), source, null))
		for ((target, text) in declaration.translations()) {
			add(EntryKey(key, target), Candidate(text, source, original))
		}
	}

	private fun add(key: EntryKey, candidate: Candidate): Selection {
		val selection = selections[key]
		if (selection != null) {
			selection.add(candidate)
			return selection
		}
		return Selection(candidate).also { selections[key] = it }
	}

	fun resolve(): Map<LanguageFile, Map<String, String>> {
		val files = Object2ObjectLinkedOpenHashMap<LanguageFile, MutableMap<String, String>>()
		val entries = selections.object2ObjectEntrySet()
		var iterator = entries.fastIterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			val key = entry.key
			val selection = entry.value
			selection.verifyText(key)

			val winner = selection.winner
			val variantKey = if (key.locale == EN_US) key.copy(locale = EN_UD) else null
			val invert = variantKey != null && !selections.containsKey(variantKey)
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

		iterator = entries.fastIterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			entry.value.verifyPlaceholders(entry.key)
		}
		return files
	}
}
