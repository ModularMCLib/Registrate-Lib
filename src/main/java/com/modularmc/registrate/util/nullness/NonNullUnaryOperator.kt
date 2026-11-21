package com.modularmc.registrate.util.nullness

/** Transforms a value without changing its type, as used by builder property callbacks. */
fun interface NonNullUnaryOperator<T : Any> : NonNullFunction<T, T> {
	/** Composes transformations in call order and propagates failures from either. */
	fun <V> andThen(after: NonNullUnaryOperator<T>): NonNullUnaryOperator<T> = NonNullUnaryOperator { after.apply(apply(it)) }

	companion object {
		@JvmStatic
		fun <T : Any> identity(): NonNullUnaryOperator<T> = NonNullUnaryOperator { it }
	}
}
