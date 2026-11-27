package com.modularmc.registrate.localization.annotation

/**
 * 声明一个翻译键。字段可以省略 [key]，按 [namePrefix]、[valuePrefix]、类级 [TransVariants] 的顺序取键。
 * 未指定前缀时使用字段的编译期 String 常量值。模组文件只声明一个模组时，可以省略 [modId]。
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
annotation class Trans(
	val modId: String = "",
	val key: String = "",
	val source: String,
	val namespace: String = "",
	val translations: Array<Translation> = [],
	val namePrefix: String = "",
	val valuePrefix: String = "",
)
