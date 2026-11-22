package com.modularmc.registrate.localization.annotation

/** Adds a translation for each variant. [modId] may be omitted when the file declares a single mod. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
annotation class TransVariants(val modId: String = "", val keyPrefix: String, val value: Array<Variant>, val namespace: String = "")
