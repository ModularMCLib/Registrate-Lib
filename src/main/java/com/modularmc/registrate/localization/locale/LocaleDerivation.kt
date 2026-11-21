package com.modularmc.registrate.localization.locale

import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.MinecraftLocale.EN_UD
import com.modularmc.registrate.localization.MinecraftLocale.EN_US
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.localization.resolution.MinecraftTextFormat

/** Defines how DataGen derives a missing target from text resolved for the input locale. */
class LocaleDerivation private constructor(
	val input: MinecraftLocale,
	val output: MinecraftLocale,
	internal val kind: Kind,
	private val transform: (String, Collection<String>, TranslationKey, LocalizationDeclaration) -> String,
) {
	constructor(input: MinecraftLocale, output: MinecraftLocale, converter: LocaleConverter) : this(
		input,
		output,
		Kind.VARIANT,
		{ text, symbols, key, declaration ->
			val context = LocaleConverter.Context(key, input, output, declaration)
			MinecraftTextFormat.convert(text, symbols) { converter.convert(it, context) }
		},
	)

	internal fun derive(text: String, symbols: Collection<String>, key: TranslationKey, declaration: LocalizationDeclaration): String = transform(text, symbols, key, declaration)

	companion object {
		internal val UPSIDE_DOWN = LocaleDerivation(EN_US, EN_UD, Kind.VARIANT) { text, symbols, _, _ -> MinecraftTextFormat.upsideDown(text, symbols) }

		/** Copies the resolved input as a display fallback for a missing target. */
		@JvmStatic
		fun fallback(input: MinecraftLocale, output: MinecraftLocale): LocaleDerivation = LocaleDerivation(input, output, Kind.FALLBACK) { text, _, _, _ -> text }
	}
}
