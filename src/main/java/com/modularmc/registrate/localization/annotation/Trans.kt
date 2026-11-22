package com.modularmc.registrate.localization.annotation

/** Adds one translation key. [modId] may be omitted when the file declares a single mod. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
annotation class Trans(val modId: String = "", val key: String, val source: String, val namespace: String = "", val translations: Array<Translation> = [])
