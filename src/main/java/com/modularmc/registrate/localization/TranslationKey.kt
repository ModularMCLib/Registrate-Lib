package com.modularmc.registrate.localization

/** 由 Minecraft 使用的原始翻译键和负责输出它的语言文件命名空间组成。 */
@JvmRecord
data class TranslationKey(val namespace: String, val value: String)
