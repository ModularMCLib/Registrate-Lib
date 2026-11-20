package com.modularmc.registrate.localization.terminology

import com.modularmc.registrate.localization.MinecraftLocale

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

import java.util.EnumSet
import java.util.Optional

/** Terminology defined by one mod. Register it during mod setup and query it alongside declarations. */
class LocalizationCatalog {
	@JvmRecord
	data class GlossaryTerm(val id: String, val source: String, val domains: List<Domain>, val preferredTranslations: Map<MinecraftLocale, String>)

	@JvmRecord
	data class Unit(val id: String, val symbol: String, val names: Map<MinecraftLocale, String>)

	private val domains = EnumSet.noneOf(Domain::class.java)
	private val terms = Object2ObjectOpenHashMap<String, GlossaryTerm>()
	private val units = Object2ObjectOpenHashMap<String, Unit>()

	fun register(domain: Domain) {
		domains.add(domain)
	}

	fun register(term: GlossaryTerm) {
		terms[term.id] = term
	}

	fun register(unit: Unit) {
		units[unit.id] = unit
	}

	fun hasDomain(domain: Domain): Boolean = domains.contains(domain)

	fun glossary(id: String): Optional<GlossaryTerm> = Optional.ofNullable(terms[id])

	fun unit(id: String): Optional<Unit> = Optional.ofNullable(units[id])
}
