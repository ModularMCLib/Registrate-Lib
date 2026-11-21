package com.modularmc.registrate.builders.base

import com.modularmc.registrate.util.entry.RegistryEntry
import com.modularmc.registrate.util.nullness.NonNullFunction
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.neoforged.neoforge.registries.DeferredHolder

/** Submits a completed builder to its owner during mod setup. The creator runs at registry time. */
interface BuilderCallback {
	/** Queues an entry with its holder factory and returns the resulting deferred holder. */
	fun <R : Any, T : R> accept(
		name: String,
		type: ResourceKey<out Registry<R>>,
		builder: Builder<R, T, *, *>,
		factory: NonNullSupplier<out T>,
		entryFactory: NonNullFunction<DeferredHolder<R, T>, out RegistryEntry<R, T>>,
	): RegistryEntry<R, T>

	/** Queues an entry using the default holder wrapper for its registry. */
	fun <R : Any, T : R> accept(name: String, type: ResourceKey<out Registry<R>>, builder: Builder<R, T, *, *>, factory: NonNullSupplier<out T>): RegistryEntry<R, T> =
		accept(name, type, builder, factory) { RegistryEntry(builder.owner, it) }
}
