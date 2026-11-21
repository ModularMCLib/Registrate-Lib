package com.modularmc.registrate.builders.base

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey

open class NoConfigBuilder<R : Any, T : R, P : Any>(
	owner: AbstractRegistrate<*>,
	parent: P,
	name: String,
	callback: BuilderCallback,
	registryType: ResourceKey<out Registry<R>>,
	private val factory: NonNullSupplier<T>,
) : AbstractBuilder<R, T, P, NoConfigBuilder<R, T, P>>(owner, parent, name, callback, registryType) {
	override fun createEntry(): T = factory.get()
}
