package com.modularmc.registrate.localization.terminology

import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TextType
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList

import java.util.EnumSet
import java.util.Optional

/** Terminology defined by one mod. Register it during mod setup and query it alongside declarations. */
class LocalizationCatalog {
	@JvmRecord
	data class GlossaryTerm @JvmOverloads constructor(
		val id: String,
		val source: String,
		val domains: List<Domain>,
		val preferredTranslations: Map<MinecraftLocale, String>,
		val textTypes: Set<TextType> = emptySet(),
		val acceptedTranslations: Map<MinecraftLocale, Set<String>> = emptyMap(),
		val forbiddenTranslations: Map<MinecraftLocale, Set<String>> = emptyMap(),
	)

	@JvmRecord
	data class Unit(val id: String, val symbol: String, val names: Map<MinecraftLocale, String>)

	private val domains = EnumSet.noneOf(Domain::class.java)
	private val indexedGlossary = IndexedGlossary()
	private val glossaries = ObjectArrayList<Glossary>(1).apply { add(indexedGlossary) }
	private val units = Object2ObjectOpenHashMap<String, Unit>()

	fun register(domain: Domain) {
		domains.add(domain)
	}

	fun register(term: GlossaryTerm) {
		indexedGlossary.register(term)
	}

	fun register(glossary: Glossary) {
		glossaries.add(glossary)
	}

	fun register(unit: Unit) {
		units[unit.id] = unit
	}

	fun hasDomain(domain: Domain): Boolean = domains.contains(domain)

	fun glossary(id: String): Optional<GlossaryTerm> = Optional.ofNullable(findTerm(id))

	fun unit(id: String): Optional<Unit> = Optional.ofNullable(units[id])

	internal fun resolveTerm(key: TranslationKey, declaration: LocalizationDeclaration, source: TranslationSource): GlossaryTerm? {
		if (declaration.glossary.isNotEmpty() || declaration.glossaryIds.isNotEmpty()) {
			resolveExplicitTerm(key, declaration, source)?.let { return it }
		}
		if (glossaries.size == 1 && indexedGlossary.isEmpty()) return null

		val label = declaration.source.lowercase()
		val type = declaration.type
		val domains = declaration.domains
		var best: GlossaryTerm? = null
		var other: GlossaryTerm? = null
		var bestScore = -1
		for (glossary in glossaries) {
			for (term in glossary.findBySource(label)) {
				val domainMatches = term.domains.any { it in domains }
				if (domains.isNotEmpty() && term.domains.isNotEmpty() && !domainMatches) continue
				val typeMatches = type != null && type in term.textTypes
				if (type != null && term.textTypes.isNotEmpty() && !typeMatches) continue

				val score = (if (domainMatches) 1 else 0) + (if (typeMatches) 1 else 0)
				if (score > bestScore) {
					bestScore = score
					best = term
					other = null
				} else if (score == bestScore && term.id != best?.id) {
					other = term
				}
			}
		}
		val selected = best ?: return null
		if (other != null) ambiguousTerm(key, source, selected, other)
		return selected
	}

	private fun resolveExplicitTerm(key: TranslationKey, declaration: LocalizationDeclaration, source: TranslationSource): GlossaryTerm? {
		var selected: GlossaryTerm? = null

		fun consider(term: GlossaryTerm) {
			if (!term.source.equals(declaration.source, ignoreCase = true)) return
			val previous = selected
			if (previous != null && previous.id != term.id) ambiguousTerm(key, source, previous, term)
			selected = term
		}

		for (term in declaration.glossary) consider(term)
		for (id in declaration.glossaryIds) {
			val term = requireNotNull(findTerm(id)) { "Unknown glossary term '$id' for $key at ${source.location}" }
			consider(term)
		}
		return selected
	}

	private fun findTerm(id: String): GlossaryTerm? = glossaries.firstNotNullOfOrNull { it.find(id) }

	private fun ambiguousTerm(key: TranslationKey, source: TranslationSource, first: GlossaryTerm, second: GlossaryTerm): Nothing =
		throw IllegalArgumentException("Ambiguous glossary term for $key at ${source.location}: ${first.id} or ${second.id}")

	internal companion object {
		val EMPTY = LocalizationCatalog()
	}
}
