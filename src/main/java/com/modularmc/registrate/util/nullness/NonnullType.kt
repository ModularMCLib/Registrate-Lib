package com.modularmc.registrate.util.nullness

import javax.annotation.Nonnull

/** 供 Java 调用方识别非空类型的类型使用注解。 */
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
