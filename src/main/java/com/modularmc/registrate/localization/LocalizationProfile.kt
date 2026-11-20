package com.modularmc.registrate.localization

import com.modularmc.registrate.localization.terminology.Domain
import com.modularmc.registrate.localization.terminology.LocalizationCatalog.GlossaryTerm

/** Reusable domain, text type and glossary defaults for a localization declaration. */
@JvmRecord
data class LocalizationProfile(val domains: List<Domain>, val type: TextType?, val glossary: List<GlossaryTerm>) {
	companion object {
		internal val EMPTY = LocalizationProfile(emptyList(), null, emptyList())
	}
}
