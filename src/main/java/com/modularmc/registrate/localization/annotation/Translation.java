package com.modularmc.registrate.localization.annotation;

import com.modularmc.registrate.localization.MinecraftLocale;

import java.lang.annotation.Target;

/** Explicit target text supplied by an annotated declaration. */
@Target({})
public @interface Translation {

    MinecraftLocale locale();

    String text();
}
