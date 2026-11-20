package com.modularmc.registrate.util.nullness

import java.util.function.BiConsumer

/** Consumes two non-null values synchronously in registration or DataGen callbacks. */
fun interface NonNullBiConsumer<T : Any, U : Any> : BiConsumer<T, U> {
	/** Consumes both values on the caller's thread and propagates callback failures. */
	override fun accept(first: T, second: U)

	companion object {
		@JvmStatic
		fun <T : Any, U : Any> noop(): NonNullBiConsumer<T, U> = NonNullBiConsumer { _, _ -> }
	}
}
