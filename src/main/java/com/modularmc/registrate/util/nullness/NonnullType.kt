package com.modularmc.registrate.util.nullness

import javax.annotation.Nonnull

/** Non-null type-use annotation retained for Java consumers. */
@MustBeDocumented
@Nonnull
@Retention(AnnotationRetention.RUNTIME)
@Target(
	AnnotationTarget.FIELD,
	AnnotationTarget.FUNCTION,
	AnnotationTarget.PROPERTY_GETTER,
	AnnotationTarget.PROPERTY_SETTER,
	AnnotationTarget.VALUE_PARAMETER,
	AnnotationTarget.TYPE_PARAMETER,
	AnnotationTarget.TYPE,
)
annotation class NonnullType
