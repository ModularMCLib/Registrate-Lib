package com.modularmc.registrate.localization.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Adds one translation key to the named mod's language declarations. */
@Retention(RetentionPolicy.CLASS)
@Target({ ElementType.TYPE, ElementType.FIELD })
public @interface Trans {

    String modId();

    String namespace() default "";

    String key();

    String source();

    Translation[] translations() default {};
}
