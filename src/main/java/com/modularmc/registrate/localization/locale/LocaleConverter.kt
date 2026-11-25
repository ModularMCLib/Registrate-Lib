package com.modularmc.registrate.localization.locale

import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TranslationKey

/**
 * 供数据生成流程在派生目标语言时转换纯文本片段。
 * 同一条译文可能分多次调用转换器，结果应只取决于输入和转换上下文。
 */
fun interface LocaleConverter {
	/**
	 * 转换一个纯文本片段。
	 *
	 * @param text 待转换的文本片段
	 * @param context 包含源语言、目标语言和声明信息的上下文
	 * @return 转换后的文本片段
	 */
	fun convert(text: String, context: Context): String

	/** 传给转换器的声明上下文，包含翻译键、输入和输出语言以及完整的本地化声明。 */
	@JvmRecord
	data class Context(val key: TranslationKey, val input: MinecraftLocale, val output: MinecraftLocale, val declaration: LocalizationDeclaration)
}
