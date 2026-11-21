package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.neoforged.neoforge.registries.DeferredHolder

import java.util.Optional
import java.util.function.Predicate

/** A deferred holder with access to its registration metadata and siblings of the same name. */
open class RegistryEntry<R : Any, S : R>(private val owner: AbstractRegistrate<*>, holder: DeferredHolder<R, S>) :
	DeferredHolder<R, S>(holder.key),
	NonNullSupplier<S> {
	/** Returns the declarations associated with this entry, even before the holder binds. */
	open fun metadata(): EntryMetadata = owner.getEntryMetadata(key)

	open fun <X : Any, Y : X> getSibling(registryType: ResourceKey<out Registry<X>>): RegistryEntry<X, Y> = owner.get(id.path, registryType)

	open fun <X : Any, Y : X> getSiblingOptional(registryType: ResourceKey<out Registry<X>>): Optional<RegistryEntry<X, Y>> = owner.getOptional(id.path, registryType)

	open fun <X : Any, Y : X> getSibling(registry: Registry<X>): RegistryEntry<X, Y> = getSibling(registry.key())

	open fun <X : Any, Y : X> getSiblingOptional(registry: Registry<X>): Optional<RegistryEntry<X, Y>> = getSiblingOptional(registry.key())

	open fun filter(predicate: Predicate<R>): Optional<RegistryEntry<R, S>> = if (predicate.test(get())) Optional.of(this) else Optional.empty()

	open fun <X> `is`(entry: X): Boolean = get() === entry

	companion object {
		@JvmStatic
		@Suppress("UNCHECKED_CAST")
		protected fun <E : RegistryEntry<*, *>> cast(type: Class<in E>, entry: RegistryEntry<*, *>): E = type.cast(entry) as E
	}
}
