package com.modularmc.registrate.localization.terminology

import com.modularmc.registrate.localization.terminology.Terminology.GlossaryTerm
import com.modularmc.registrate.localization.terminology.Terminology.TermRelation
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

internal class IndexedGlossary : Glossary {
	private data class RelationKey(val id: String, val relation: TermRelation)
	private data class RelationEdge(val key: RelationKey, val targetId: String)

	private val terms = Object2ObjectOpenHashMap<String, GlossaryTerm>()
	private val termsBySource = Object2ObjectOpenHashMap<String, ObjectArrayList<GlossaryTerm>>()
	private val relatedIds = Object2ObjectOpenHashMap<RelationKey, ObjectLinkedOpenHashSet<String>>()
	private val relationCounts = Object2ObjectOpenHashMap<RelationKey, Object2IntOpenHashMap<String>>()
	private val relationEdges = Object2ObjectOpenHashMap<String, ObjectArrayList<RelationEdge>>()

	fun isEmpty(): Boolean = terms.isEmpty()

	fun register(term: GlossaryTerm) {
		val previous = terms[term.id]
		if (previous != null) {
			forEachForm(previous) { source ->
				val oldTerms = termsBySource.getValue(source)
				oldTerms.remove(previous)
				if (oldTerms.isEmpty) termsBySource.remove(source)
			}
			relationEdges.remove(term.id)?.forEach(::removeRelation)
		}
		terms[term.id] = term
		forEachForm(term) { source ->
			termsBySource.computeIfAbsent(source) { ObjectArrayList(1) }.add(term)
		}
		val edges = ObjectArrayList<RelationEdge>()
		for ((relation, ids) in term.relations) {
			for (relatedId in ids) {
				addRelation(RelationKey(term.id, relation), relatedId, edges)
				if (relation == TermRelation.SYNONYM) {
					addRelation(RelationKey(relatedId, relation), term.id, edges)
				}
			}
		}
		relationEdges[term.id] = edges
	}

	override fun find(id: String): GlossaryTerm? = terms[id]

	override fun findBySource(normalizedSource: String): Iterable<GlossaryTerm> = termsBySource[normalizedSource] ?: emptyList()

	override fun findRelated(id: String, relation: TermRelation): Iterable<GlossaryTerm> {
		val ids = relatedIds[RelationKey(id, relation)] ?: return emptyList()
		val related = ObjectArrayList<GlossaryTerm>(ids.size)
		for (relatedId in ids) terms[relatedId]?.let(related::add)
		return related
	}

	private fun addRelation(key: RelationKey, targetId: String, edges: ObjectArrayList<RelationEdge>) {
		relationCounts.computeIfAbsent(key) { Object2IntOpenHashMap() }.addTo(targetId, 1)
		relatedIds.computeIfAbsent(key) { ObjectLinkedOpenHashSet() }.add(targetId)
		edges.add(RelationEdge(key, targetId))
	}

	private fun removeRelation(edge: RelationEdge) {
		val counts = relationCounts.getValue(edge.key)
		if (counts.addTo(edge.targetId, -1) == 1) {
			counts.removeInt(edge.targetId)
			if (counts.isEmpty()) relationCounts.remove(edge.key)
			val ids = relatedIds.getValue(edge.key)
			ids.remove(edge.targetId)
			if (ids.isEmpty()) relatedIds.remove(edge.key)
		}
	}

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
