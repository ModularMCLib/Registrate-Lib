package com.modularmc.registrate.localization.annotation

/** 变体组中的一条声明。该变体的键后缀接到组的公共前缀后。 */
@Retention(AnnotationRetention.BINARY)
@Target()
annotation class Variant(val id: String, val source: String, val translations: Array<Translation> = [])
