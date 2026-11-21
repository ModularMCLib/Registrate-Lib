package com.modularmc.registrate.providers.core

import net.minecraft.core.Registry
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.resources.ResourceKey

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.util.concurrent.ConcurrentHashMap

open class DataProviderInitializer {
	private val datapackEntries = RegistrySetBuilder()
	private val dependencies = ConcurrentHashMap<ProviderType<*>, ProviderType<out RegistrateLookupFillerProvider>>()

	init {
		addDependency(ProviderType.ITEM_TAGS, ProviderType.BLOCK_TAGS)
	}

	open fun getDatapackRegistryProviders(): RegistrySetBuilder = datapackEntries

	open fun getSortedProviders(): List<Sorted> {
		val remaining = RegistrateDataProvider.TYPES.entries.sortedBy { it.key }.toMutableList()
		val result = ArrayList<Sorted>(remaining.size)
		val added = ObjectOpenHashSet<ProviderType<*>>(remaining.size)
		while (remaining.isNotEmpty()) {
			val previousSize = remaining.size
			val iterator = remaining.iterator()
			while (iterator.hasNext()) {
				val (id, type) = iterator.next()
				val parent = dependencies[type]
				if (parent == null || parent in added) {
					result.add(Sorted(id, type, parent))
					added.add(type)
					iterator.remove()
				}
			}
			check(remaining.size != previousSize) { "Looping dependency detected: $remaining" }
		}
		return result
	}

	open fun <T : Any> add(registry: ResourceKey<Registry<T>>, provider: RegistrySetBuilder.RegistryBootstrap<T>) {
		datapackEntries.add(registry, provider)
	}

	open fun addDependency(dependent: ProviderType<*>, parent: ProviderType<out RegistrateLookupFillerProvider>) {
		check(dependencies.putIfAbsent(dependent, parent) == null) { "Providers can have only 1 prerequisite" }
	}

	@JvmRecord
	data class Sorted(val id: String, val type: ProviderType<*>, val parent: ProviderType<out RegistrateLookupFillerProvider>?)
}
