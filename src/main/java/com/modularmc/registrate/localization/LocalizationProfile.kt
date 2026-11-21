package com.modularmc.registrate.localization

import com.modularmc.registrate.localization.terminology.Domain
import com.modularmc.registrate.localization.terminology.LocalizationCatalog.GlossaryTerm

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet

/** Reusable domain, text type and glossary defaults for a localization declaration. */
@JvmRecord
data class LocalizationProfile @JvmOverloads constructor(
	val domains: List<Domain>,
	val type: TextType?,
	val glossary: List<GlossaryTerm>,
	val glossaryIds: List<String> = emptyList(),
	val unitIds: List<String> = emptyList(),
) {
	/** Builds a profile from already constructed profiles. Included values take precedence over inherited defaults. */
	class Builder {
		private var defaults = EMPTY
		private var parent = EMPTY
		private val included = ObjectArrayList<LocalizationProfile>()

		fun defaults(profile: LocalizationProfile) = apply { defaults = profile }

		fun inherits(profile: LocalizationProfile) = apply { parent = profile }

		fun include(vararg profiles: LocalizationProfile) = apply { included.addAll(profiles.asList()) }

		fun build(): LocalizationProfile {
			val domains = ObjectLinkedOpenHashSet<Domain>()
			val glossary = Object2ObjectLinkedOpenHashMap<String, GlossaryTerm>()
			val glossaryIds = ObjectLinkedOpenHashSet<String>()
			val units = ObjectLinkedOpenHashSet<String>()
			var type: TextType? = null
			for (profile in included) {
				domains.addAll(profile.domains)
				glossaryIds.addAll(profile.glossaryIds)
				units.addAll(profile.unitIds)
				for (term in profile.glossary) {
					val existing = glossary.putIfAbsent(term.id, term)
					require(existing == null || existing == term) { "Included profiles disagree on glossary term '${term.id}'" }
				}
				profile.type?.let { candidate ->
					require(type == null || type == candidate) { "Included profiles disagree on text type: $type and $candidate" }
					type = candidate
				}
			}
			val glossaryParent = if (parent.glossary.isEmpty() && parent.glossaryIds.isEmpty()) defaults else parent
			val useIncludedGlossary = glossary.isNotEmpty() || glossaryIds.isNotEmpty()
			return LocalizationProfile(
				if (domains.isEmpty()) parent.domains.ifEmpty { defaults.domains } else ObjectArrayList(domains),
				type ?: parent.type ?: defaults.type,
				if (useIncludedGlossary) ObjectArrayList(glossary.values) else glossaryParent.glossary,
				if (useIncludedGlossary) ObjectArrayList(glossaryIds) else glossaryParent.glossaryIds,
				if (units.isEmpty()) parent.unitIds.ifEmpty { defaults.unitIds } else ObjectArrayList(units),
			)
		}
	}

	companion object {
		internal val EMPTY = LocalizationProfile(emptyList(), null, emptyList())
	}
}
