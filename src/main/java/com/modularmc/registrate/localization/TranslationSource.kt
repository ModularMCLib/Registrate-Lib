package com.modularmc.registrate.localization

/** Identifies a translation's priority and the declaration that supplied it. */
@JvmRecord
data class TranslationSource(val kind: Kind, val location: String) {
	enum class Kind(internal val priority: Int) {
		FALLBACK(0),
		VARIANT(1),
		AUTOMATIC(2),
		GLOSSARY(3),
		IMPORTED(4),
		DECLARED(5),
	}
}
