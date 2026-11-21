package com.modularmc.registrate.localization.annotation

/** One entry of a variant group, appended to the group's key prefix. */
@Retention(AnnotationRetention.BINARY)
@Target()
annotation class Variant(val id: String, val source: String, val translations: Array<Translation> = [])
