package com.modularmc.registrate.localization

import com.modularmc.registrate.localization.terminology.Domain
import com.modularmc.registrate.localization.terminology.Terminology.GlossaryTerm

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet

/** Reusable domain, text type and glossary defaults for a localization declaration. */
@JvmRecord
data class DeclarationDefaults @JvmOverloads constructor(
	val domains: List<Domain>,
	val type: TextType?,
	val glossary: List<GlossaryTerm>,
	val glossaryIds: List<String> = emptyList(),
	val unitIds: List<String> = emptyList(),
) {
	/** Combines existing defaults so that included values take precedence over inherited defaults. */
	class Builder {
		private var defaults = EMPTY
		private var parent = EMPTY
		private val included = ObjectArrayList<DeclarationDefaults>()

		fun defaults(defaults: DeclarationDefaults) = apply { this.defaults = defaults }

		fun inherits(parent: DeclarationDefaults) = apply { this.parent = parent }

		fun include(vararg defaults: DeclarationDefaults) = apply { included.addAll(defaults.asList()) }

		fun build(): DeclarationDefaults {
			val domains = ObjectLinkedOpenHashSet<Domain>()
			val glossary = Object2ObjectLinkedOpenHashMap<String, GlossaryTerm>()
			val glossaryIds = ObjectLinkedOpenHashSet<String>()
			val units = ObjectLinkedOpenHashSet<String>()
			var type: TextType? = null
			for (defaults in included) {
				domains.addAll(defaults.domains)
				glossaryIds.addAll(defaults.glossaryIds)
				units.addAll(defaults.unitIds)
				for (term in defaults.glossary) {
					val existing = glossary.putIfAbsent(term.id, term)
					require(existing == null || existing == term) { "Included defaults disagree on glossary term '${term.id}'" }
				}
				defaults.type?.let { candidate ->
					require(type == null || type == candidate) { "Included defaults disagree on text type: ${type?.id} and ${candidate.id}" }
					type = candidate
				}
			}
			val glossaryParent = if (parent.glossary.isEmpty() && parent.glossaryIds.isEmpty()) defaults else parent
			val useIncludedGlossary = glossary.isNotEmpty() || glossaryIds.isNotEmpty()
			return DeclarationDefaults(
				if (domains.isEmpty()) parent.domains.ifEmpty { defaults.domains } else ObjectArrayList(domains),
				type ?: parent.type ?: defaults.type,
				if (useIncludedGlossary) ObjectArrayList(glossary.values) else glossaryParent.glossary,
				if (useIncludedGlossary) ObjectArrayList(glossaryIds) else glossaryParent.glossaryIds,
				if (units.isEmpty()) parent.unitIds.ifEmpty { defaults.unitIds } else ObjectArrayList(units),
			)
		}
	}

	companion object {
		internal val EMPTY = DeclarationDefaults(emptyList(), null, emptyList())
	}
}
