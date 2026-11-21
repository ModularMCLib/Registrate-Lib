package com.modularmc.registrate.util.nullness

import java.util.function.BiFunction

/** Computes a non-null result from two non-null inputs on the caller's thread. */
fun interface NonNullBiFunction<T : Any, U : Any, R : Any> : BiFunction<T, U, R> {
	/** Invokes the transform synchronously and propagates callback failures. */
	override fun apply(first: T, second: U): R
}
