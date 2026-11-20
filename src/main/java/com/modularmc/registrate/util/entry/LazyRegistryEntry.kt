package com.modularmc.registrate.util.entry

import com.modularmc.registrate.util.nullness.NonNullSupplier

open class LazyRegistryEntry<R : Any, T : R>(supplier: NonNullSupplier<out RegistryEntry<R, T>>) : NonNullSupplier<T> {
	private val holder by lazy(LazyThreadSafetyMode.NONE) { supplier.get() }

	override fun get(): T = holder.get()
}
