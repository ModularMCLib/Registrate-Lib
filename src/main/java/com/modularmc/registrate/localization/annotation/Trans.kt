package com.modularmc.registrate.localization.annotation

/** 声明一个翻译键。模组文件只声明一个模组时，可以省略 [modId]。 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
annotation class Trans(val modId: String = "", val key: String, val source: String, val namespace: String = "", val translations: Array<Translation> = [])
