package com.modularmc.registrate.localization

/** Identifies a translation's priority and the declaration that supplied it. */
@JvmRecord
data class TranslationSource(val kind: Kind, val location: String) {
	enum class Kind {
		FALLBACK,
		VARIANT,
		AUTOMATIC,
		GLOSSARY,
		DECLARED,
	}
}
