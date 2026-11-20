package com.modularmc.registrate.providers

import com.modularmc.registrate.builders.base.Builder
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

/** The deferred entry and the ID assigned to it for DataGen. */
data class DataGenContext<R : Any, E : R>(private val supplier: NonNullSupplier<E>, val name: String, val id: ResourceLocation) : NonNullSupplier<E> by supplier {
	val entry: E
		get() = supplier.get()

	companion object {
		@JvmStatic
		@Deprecated("The builder already identifies its registry", ReplaceWith("from(builder)"))
		@Suppress("UNUSED_PARAMETER")
		fun <R : Any, E : R> from(builder: Builder<R, E, *, *>, type: ResourceKey<out Registry<R>>): DataGenContext<R, E> = from(builder)

		@JvmStatic
		fun <R : Any, E : R> from(builder: Builder<R, E, *, *>): DataGenContext<R, E> {
			val owner = builder.owner
			val name = builder.name
			val entry = owner.get<R, E>(name, builder.registryKey)
			return DataGenContext(entry, name, entry.id)
		}
	}
}
