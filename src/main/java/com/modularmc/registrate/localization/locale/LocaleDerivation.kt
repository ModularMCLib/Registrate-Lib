package com.modularmc.registrate.localization.locale

import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.MinecraftLocale.EN_GB
import com.modularmc.registrate.localization.MinecraftLocale.EN_UD
import com.modularmc.registrate.localization.MinecraftLocale.EN_US
import com.modularmc.registrate.localization.MinecraftLocale.ZH_CN
import com.modularmc.registrate.localization.MinecraftLocale.ZH_TW
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.localization.resolution.MinecraftTextFormat

/** 定义目标语言缺少译文时的派生规则。数据生成流程以输入语言已解析的文本为基础。 */
class LocaleDerivation private constructor(
	val input: MinecraftLocale,
	val output: MinecraftLocale,
	internal val kind: Kind,
	private val transform: (String, Collection<String>, TranslationKey, LocalizationDeclaration) -> String,
) {
	/** 创建纯文本转换规则。转换器会逐段执行，格式控制、占位符和单位符号由本地化流程保留。 */
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
		internal val BRITISH_ENGLISH = LocaleDerivation(EN_US, EN_GB) { text, _ -> BritishEnglish.convert(text) }
		internal val TRADITIONAL_CHINESE = LocaleDerivation(ZH_CN, ZH_TW) { text, _ -> TraditionalChinese.convert(text) }

		/** 为目标语言配置显示回退规则。缺少译文时直接使用输入语言已解析的文本。 */
		@JvmStatic
		fun fallback(input: MinecraftLocale, output: MinecraftLocale): LocaleDerivation = LocaleDerivation(input, output, Kind.FALLBACK) { text, _, _, _ -> text }
	}
}
