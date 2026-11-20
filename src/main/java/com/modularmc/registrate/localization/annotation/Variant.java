package com.modularmc.registrate.localization.annotation;

import java.lang.annotation.Target;

/** One entry of a variant group, appended to the group's key prefix. */
@Target({})
public @interface Variant {

    String id();

    String source();

    Translation[] translations() default {};
}
