package com.modularmc.registrate.localization.annotation

/** Adds a translation for each variant under the given key prefix. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
annotation class TransVariants(val modId: String, val keyPrefix: String, val value: Array<Variant>, val namespace: String = "")
