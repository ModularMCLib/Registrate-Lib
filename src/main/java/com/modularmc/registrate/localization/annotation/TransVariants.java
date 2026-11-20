package com.modularmc.registrate.localization.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Adds one translation per variant using the supplied key prefix. */
@Retention(RetentionPolicy.CLASS)
@Target({ ElementType.TYPE, ElementType.FIELD })
public @interface TransVariants {

    String modId();

    String namespace() default "";

    String keyPrefix();

    Variant[] value();
}
