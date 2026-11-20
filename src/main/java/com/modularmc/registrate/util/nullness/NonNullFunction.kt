package com.modularmc.registrate.util.nullness

import java.util.function.Function

/** Transforms a non-null value while keeping a non-null result through composition. */
fun interface NonNullFunction<T : Any, R : Any> : Function<T, R> {
	/** Runs synchronously and propagates callback failures to the caller. */
	override fun apply(value: T): R

	fun <V : Any> andThen(after: NonNullFunction<in R, out V>): NonNullFunction<T, V> = NonNullFunction { after.apply(apply(it)) }
}
