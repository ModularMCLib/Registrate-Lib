package com.modularmc.registrate.localization.locale

import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TranslationKey

/**
 * Transforms plain text runs for a configured locale conversion during DataGen.
 * The same converter may be called for several runs of one translation. Keep it deterministic.
 */
fun interface LocaleConverter {
	/** Receives the plain run and its source, target and declaration context. Returns the converted run. */
	fun convert(text: String, context: Context): String

	@JvmRecord
	data class Context(val key: TranslationKey, val input: MinecraftLocale, val output: MinecraftLocale, val declaration: LocalizationDeclaration)
}
