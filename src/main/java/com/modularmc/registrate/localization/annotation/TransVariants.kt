package com.modularmc.registrate.localization.annotation

/**
 * 为每个变体声明翻译键。类上的声明也给直接声明的 [Trans] 字段提供前缀和归属。
 * 字段后缀默认取字段名，[useFieldValues] 启用时改用编译期 String 常量值。
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
annotation class TransVariants(val modId: String = "", val keyPrefix: String, val value: Array<Variant> = [], val namespace: String = "", val useFieldValues: Boolean = false)
