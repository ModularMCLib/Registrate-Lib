package com.modularmc.registrate.localization.terminology

import com.modularmc.registrate.localization.terminology.LocalizationCatalog.GlossaryTerm

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList

internal class IndexedGlossary : Glossary {
	private val terms = Object2ObjectOpenHashMap<String, GlossaryTerm>()
	private val termsBySource = Object2ObjectOpenHashMap<String, ObjectArrayList<GlossaryTerm>>()

	fun isEmpty(): Boolean = terms.isEmpty()

	fun register(term: GlossaryTerm) {
		val previous = terms.put(term.id, term)
		if (previous != null) {
			val oldSource = previous.source.lowercase()
			val oldTerms = termsBySource.getValue(oldSource)
			oldTerms.remove(previous)
			if (oldTerms.isEmpty) termsBySource.remove(oldSource)
		}
		termsBySource.computeIfAbsent(term.source.lowercase()) { ObjectArrayList(1) }.add(term)
	}

	override fun find(id: String): GlossaryTerm? = terms[id]

	override fun findBySource(normalizedSource: String): Iterable<GlossaryTerm> = termsBySource[normalizedSource] ?: emptyList()
}
