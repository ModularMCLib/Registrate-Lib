package com.modularmc.registrate.localization

import com.modularmc.registrate.localization.terminology.Domain
import com.modularmc.registrate.localization.terminology.LocalizationCatalog.GlossaryTerm

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

/** A source declaration and its explicit translations, resolved against the owning mod's source locale. */
@JvmRecord
data class LocalizationDeclaration(val source: String, val translations: Map<MinecraftLocale, String>, val domains: List<Domain>, val type: TextType?, val glossary: List<GlossaryTerm>) {
	class Builder {
		private lateinit var source: String
		private var translations: Object2ObjectOpenHashMap<MinecraftLocale, String>? = null
		private var domains: List<Domain>? = null
		private var type: TextType? = null
		private var glossary: List<GlossaryTerm>? = null
		private var profile = LocalizationProfile.EMPTY

		fun source(source: String) = apply { this.source = source }

		fun type(type: TextType?) = apply { this.type = type }

		fun profile(profile: LocalizationProfile) = apply { this.profile = profile }

		fun translation(locale: MinecraftLocale, text: String) = apply {
			val texts = translations ?: Object2ObjectOpenHashMap<MinecraftLocale, String>(2).also { translations = it }
			texts[locale] = text
		}

		fun domain(vararg domains: Domain) = apply { this.domains = domains.toList() }

		fun glossary(vararg terms: GlossaryTerm) = apply { glossary = terms.toList() }

		fun build() = LocalizationDeclaration(
			source,
			translations?.toMap() ?: emptyMap(),
			domains ?: profile.domains,
			type ?: profile.type,
			glossary ?: profile.glossary,
		)
	}

	companion object {
		@JvmStatic
		fun sourceText(source: String) = LocalizationDeclaration(source, emptyMap(), emptyList(), null, emptyList())
	}
}
