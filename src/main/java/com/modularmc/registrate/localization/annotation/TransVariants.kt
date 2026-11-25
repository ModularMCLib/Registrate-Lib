package com.modularmc.registrate.localization.annotation

/** 为每个变体声明翻译键。模组文件只声明一个模组时，可以省略 [modId]。 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
annotation class TransVariants(val modId: String = "", val keyPrefix: String, val value: Array<Variant>, val namespace: String = "")
