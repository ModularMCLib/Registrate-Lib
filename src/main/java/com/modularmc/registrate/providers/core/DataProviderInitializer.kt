package com.modularmc.registrate.providers.core

import net.minecraft.core.Registry
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.resources.ResourceKey

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.util.concurrent.ConcurrentHashMap

/** 在收集数据任务前保存数据包注册表的引导定义和提供器依赖，用于安排构造及运行顺序。 */
open class DataProviderInitializer {
	private val datapackEntries = RegistrySetBuilder()
	private val dependencies = ConcurrentHashMap<ProviderType<*>, ProviderType<out RegistrateLookupFillerProvider>>()

	init {
		addDependency(ProviderType.ITEM_TAGS, ProviderType.BLOCK_TAGS)
	}

	/** 返回累积的注册表引导定义，供数据包提供器补齐本轮查询。 */
	open fun getDatapackRegistryProviders(): RegistrySetBuilder = datapackEntries

	/** 按依赖先后排列已登记的提供器，无依赖约束的项目按名称排序处理。依赖无法满足时抛出 [IllegalStateException]。 */
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

	/** 为指定数据包注册表增加条目引导回调。回调在构造本轮注册表查询时执行。 */
	open fun <T : Any> add(registry: ResourceKey<Registry<T>>, provider: RegistrySetBuilder.RegistryBootstrap<T>) {
		datapackEntries.add(registry, provider)
	}

	/** 设置 [dependent] 使用的查询来源。每个提供器只能登记一个前置能力，重复登记时抛出 [IllegalStateException]。 */
	open fun addDependency(dependent: ProviderType<*>, parent: ProviderType<out RegistrateLookupFillerProvider>) {
		check(dependencies.putIfAbsent(dependent, parent) == null) { "Providers can have only 1 prerequisite" }
	}

	/** 按依赖排序后的提供器描述。[parent] 指定注册表查询的前置提供器，可为空。 */
	@JvmRecord
	data class Sorted(val id: String, val type: ProviderType<*>, val parent: ProviderType<out RegistrateLookupFillerProvider>?)
}
