package com.modularmc.registrate.localization.annotation

import com.modularmc.registrate.localization.MinecraftLocale

/** Explicit target text supplied by an annotated declaration. */
@Retention(AnnotationRetention.BINARY)
@Target()
annotation class Translation(val locale: MinecraftLocale, val text: String)
