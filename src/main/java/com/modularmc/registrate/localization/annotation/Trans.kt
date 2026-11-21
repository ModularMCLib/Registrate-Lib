package com.modularmc.registrate.localization.annotation

/** Adds one translation key to the named mod's language declarations. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
annotation class Trans(val modId: String, val key: String, val source: String, val namespace: String = "", val translations: Array<Translation> = [])
