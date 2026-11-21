package com.modularmc.registrate.util.nullness

import net.neoforged.neoforge.common.util.Lazy

import java.util.Objects
import java.util.function.Supplier

/** Supplies a non-null value when a registration or provider callback needs it. */
fun interface NonNullSupplier<T : Any> : Supplier<T> {
	/** Computes the value on the caller's thread and propagates supplier failures. */
	override fun get(): T

	fun lazy(): NonNullSupplier<T> = lazy(this)

	companion object {
		/** Adapts a Java supplier and reports a null result when the adapter is used. */
		@JvmStatic
		fun <T : Any> of(supplier: Supplier<T>): NonNullSupplier<T> = of(supplier) { "Unexpected null value from supplier" }

		@JvmStatic
		fun <T : Any> of(supplier: Supplier<T>, errorMessage: NonNullSupplier<String>): NonNullSupplier<T> = NonNullSupplier { Objects.requireNonNull(supplier.get(), errorMessage) }

		/** Defers computation until first use and memoizes the result through NeoForge's [Lazy]. */
		@JvmStatic
		fun <T : Any> lazy(supplier: Supplier<T>): NonNullSupplier<T> {
			val value = Lazy.of(supplier)
			return NonNullSupplier { value.get() }
		}
	}
}
