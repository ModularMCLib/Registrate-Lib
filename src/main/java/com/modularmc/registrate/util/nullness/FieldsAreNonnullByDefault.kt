package com.modularmc.registrate.util.nullness

import java.lang.annotation.ElementType
import javax.annotation.Nonnull
import javax.annotation.meta.TypeQualifierDefault

import java.lang.annotation.Target as JavaTarget

/** Marks unannotated fields in a package or type as non-null. */
@MustBeDocumented
@Nonnull
@TypeQualifierDefault(ElementType.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Suppress("DEPRECATED_JAVA_ANNOTATION") // The Java annotation can target packages.
@JavaTarget(ElementType.PACKAGE, ElementType.TYPE)
annotation class FieldsAreNonnullByDefault
