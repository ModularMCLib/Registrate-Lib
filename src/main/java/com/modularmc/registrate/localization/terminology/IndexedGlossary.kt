package com.modularmc.registrate.localization.terminology

import com.modularmc.registrate.localization.terminology.LocalizationCatalog.GlossaryTerm

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

internal class IndexedGlossary : Glossary {
	private val terms = Object2ObjectOpenHashMap<String, GlossaryTerm>()
	private val termsBySource = Object2ObjectOpenHashMap<String, ObjectArrayList<GlossaryTerm>>()

	fun isEmpty(): Boolean = terms.isEmpty()

	fun register(term: GlossaryTerm) {
		val previous = terms.put(term.id, term)
		if (previous != null) {
			forEachForm(previous) { source ->
				val oldTerms = termsBySource.getValue(source)
				oldTerms.remove(previous)
				if (oldTerms.isEmpty) termsBySource.remove(source)
			}
		}
		forEachForm(term) { source ->
			termsBySource.computeIfAbsent(source) { ObjectArrayList(1) }.add(term)
		}
	}

	override fun find(id: String): GlossaryTerm? = terms[id]

	override fun findBySource(normalizedSource: String): Iterable<GlossaryTerm> = termsBySource[normalizedSource] ?: emptyList()

	private inline fun forEachForm(term: GlossaryTerm, action: (String) -> Unit) {
		val source = term.source.lowercase()
		if (term.aliases.isEmpty()) {
			action(source)
			return
		}
		val forms = ObjectOpenHashSet<String>(term.aliases.size + 1)
		forms.add(source)
		for (alias in term.aliases) forms.add(alias.lowercase())
		forms.forEach(action)
	}
}
