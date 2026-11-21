package com.modularmc.registrate.localization

import com.modularmc.registrate.localization.terminology.Domain
import com.modularmc.registrate.localization.terminology.Terminology.GlossaryTerm

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

/** A source declaration and its explicit translations, resolved against the owning mod's source locale. */
@JvmRecord
data class LocalizationDeclaration @JvmOverloads constructor(
	val source: String,
	val translations: Map<MinecraftLocale, String>,
	val domains: List<Domain>,
	val type: TextType?,
	val glossary: List<GlossaryTerm>,
	val glossaryIds: List<String> = emptyList(),
	val unitIds: List<String> = emptyList(),
) {
	class Builder {
		private lateinit var source: String
		private var translations: Object2ObjectOpenHashMap<MinecraftLocale, String>? = null
		private var domains: List<Domain>? = null
		private var type: TextType? = null
		private var glossary: List<GlossaryTerm>? = null
		private var glossaryIds: List<String>? = null
		private var unitIds: List<String>? = null
		private var defaults = DeclarationDefaults.EMPTY

		fun source(source: String) = apply { this.source = source }

		fun type(type: TextType?) = apply { this.type = type }

		fun defaults(defaults: DeclarationDefaults) = apply { this.defaults = defaults }

		fun translation(locale: MinecraftLocale, text: String) = apply {
			val texts = translations ?: Object2ObjectOpenHashMap<MinecraftLocale, String>(2).also { translations = it }
			texts[locale] = text
		}

		fun domain(vararg domains: Domain) = apply { this.domains = domains.toList() }

		fun glossary(vararg terms: GlossaryTerm) = apply {
			glossary = terms.asList()
			glossaryIds = emptyList()
		}

		fun glossaryIds(vararg ids: String) = apply {
			glossary = emptyList()
			glossaryIds = ids.asList()
		}

		fun units(vararg ids: String) = apply { unitIds = ids.asList() }

		fun build(): LocalizationDeclaration = build(DeclarationDefaults.EMPTY)

		fun build(moduleDefaults: DeclarationDefaults): LocalizationDeclaration {
			val entryGlossary = if (defaults.glossary.isEmpty() && defaults.glossaryIds.isEmpty()) moduleDefaults else defaults
			return LocalizationDeclaration(
				source,
				translations?.toMap() ?: emptyMap(),
				domains ?: defaults.domains.ifEmpty { moduleDefaults.domains },
				type ?: defaults.type ?: moduleDefaults.type,
				glossary ?: entryGlossary.glossary,
				glossaryIds ?: entryGlossary.glossaryIds,
				unitIds ?: defaults.unitIds.ifEmpty { moduleDefaults.unitIds },
			)
		}
	}

	companion object {
		@JvmStatic
		fun sourceText(source: String) = LocalizationDeclaration(source, emptyMap(), emptyList(), null, emptyList())

		@JvmStatic
		fun sourceText(source: String, defaults: DeclarationDefaults) =
			LocalizationDeclaration(source, emptyMap(), defaults.domains, defaults.type, defaults.glossary, defaults.glossaryIds, defaults.unitIds)
	}
}
