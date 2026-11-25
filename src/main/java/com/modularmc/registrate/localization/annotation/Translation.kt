package com.modularmc.registrate.localization.annotation

import com.modularmc.registrate.localization.MinecraftLocale

/** 为注解声明提供指定语言的译文。 */
@Retention(AnnotationRetention.BINARY)
@Target()
annotation class Translation(val locale: MinecraftLocale, val text: String)
