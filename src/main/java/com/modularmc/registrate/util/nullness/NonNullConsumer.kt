package com.modularmc.registrate.util.nullness

import java.util.function.Consumer

/** Consumes a value during the calling operation. Both callbacks run in order when composed. */
fun interface NonNullConsumer<T : Any> : Consumer<T> {
	/** Runs on the caller's thread and propagates callback failures. */
	override fun accept(value: T)

	fun andThen(after: NonNullConsumer<in T>): NonNullConsumer<T> = NonNullConsumer { value ->
		accept(value)
		after.accept(value)
	}

	companion object {
		@JvmStatic
		fun <T : Any> noop(): NonNullConsumer<T> = NonNullConsumer {}
	}
}
