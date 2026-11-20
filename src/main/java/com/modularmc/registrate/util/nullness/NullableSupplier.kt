package com.modularmc.registrate.util.nullness

import java.util.Objects
import java.util.function.Supplier

/** Legacy supplier that can return null when evaluated on the caller's thread. */
@Deprecated("Use Supplier<T?>")
fun interface NullableSupplier<T : Any> : Supplier<T?> {
	/** Returns the current value, which may be absent, and propagates supplier failures. */
	override fun get(): T?

	fun getNonNull(): T = getNonNull { "Unexpected null value from supplier" }

	fun getNonNull(errorMessage: NonNullSupplier<String>): T = Objects.requireNonNull<T>(get(), errorMessage)

	fun asNonNull(): NonNullSupplier<T> = NonNullSupplier(::getNonNull)

	fun asNonNull(errorMessage: NonNullSupplier<String>): NonNullSupplier<T> = NonNullSupplier { getNonNull(errorMessage) }
}
