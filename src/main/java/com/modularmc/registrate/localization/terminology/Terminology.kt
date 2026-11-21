package com.modularmc.registrate.localization.terminology

import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TextType
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import java.util.Optional

/** Terminology defined by one mod. Register it during mod setup and query it alongside declarations. */
class Terminology {
	@JvmRecord
	data class GlossaryTerm @JvmOverloads constructor(
		val id: String,
		val source: String,
		val domains: List<Domain>,
		val preferredTranslations: Map<MinecraftLocale, String>,
		val textTypes: Set<TextType> = emptySet(),
		val acceptedTranslations: Map<MinecraftLocale, Set<String>> = emptyMap(),
		val forbiddenTranslations: Map<MinecraftLocale, Set<String>> = emptyMap(),
		val aliases: Set<String> = emptySet(),
		val relations: Map<TermRelation, Set<String>> = emptyMap(),
		val unitIds: Set<String> = emptySet(),
	) {
		internal fun matches(sourceText: String): Boolean = source.equals(sourceText, ignoreCase = true) || aliases.any { it.equals(sourceText, ignoreCase = true) }
	}

	enum class TermRelation {
		SYNONYM,
		BROADER,
		NARROWER,
		PART_OF,
		HAS_PART,
		RELATED,
	}

	@JvmRecord
	data class MeasurementUnit @JvmOverloads constructor(val id: String, val symbol: String, val names: Map<MinecraftLocale, String>, val formats: Map<MinecraftLocale, UnitFormat> = emptyMap()) {
		fun format(value: Number, locale: MinecraftLocale): String = formats[locale]?.format(value, symbol) ?: "$value $symbol"
	}

	private val domains = ObjectOpenHashSet<Domain>()
	private val indexedGlossary = IndexedGlossary()
	private val glossaries = ObjectArrayList<Glossary>(1).apply { add(indexedGlossary) }
	private val units = Object2ObjectOpenHashMap<String, MeasurementUnit>()

	fun register(domain: Domain) {
		domains.add(domain)
	}

	fun register(term: GlossaryTerm) {
		indexedGlossary.register(term)
	}

	fun register(glossary: Glossary) {
		glossaries.add(glossary)
	}

	fun register(unit: MeasurementUnit) {
		units[unit.id] = unit
	}

	fun hasDomain(domain: Domain): Boolean = domains.contains(domain)

	internal fun synonyms(term: GlossaryTerm): ObjectArrayList<GlossaryTerm> {
		val discovered = ObjectOpenHashSet<String>()
		val pending = ObjectArrayList<GlossaryTerm>(4)
		val result = ObjectArrayList<GlossaryTerm>(4)
		discovered.add(term.id)
		pending.add(term)
		result.add(term)
		var index = 0
		while (index < pending.size) {
			val current = pending[index++]
			for (glossary in glossaries) {
				for (related in glossary.findRelated(current.id, TermRelation.SYNONYM)) {
					if (discovered.add(related.id)) {
						pending.add(related)
						result.add(related)
					}
				}
			}
		}
		return result
	}

	fun glossary(id: String): Optional<GlossaryTerm> = Optional.ofNullable(findTerm(id))

	fun unit(id: String): Optional<MeasurementUnit> = Optional.ofNullable(units[id])

	internal fun unitSymbol(id: String): String = units.getValue(id).symbol

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
			if (!term.matches(declaration.source)) return
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
		val EMPTY = Terminology()
	}
}
