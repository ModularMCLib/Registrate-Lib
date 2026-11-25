package com.modularmc.registrate.providers

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.providers.core.RegistrateLookupFillerProvider

import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.data.PackOutput
import net.minecraft.data.tags.IntrinsicHolderTagsProvider
import net.minecraft.data.tags.KeyTagProvider
import net.minecraft.data.tags.TagAppender
import net.minecraft.data.tags.TagsProvider.TagLookup
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagBuilder
import net.minecraft.tags.TagKey
import net.neoforged.fml.LogicalSide

import java.util.concurrent.CompletableFuture
import java.util.function.Function

/** 为一个注册表生成标签。在数据生成回调中向对应的标签构建器添加条目。 */
interface RegistrateTagsProvider<T : Any> : RegistrateLookupFillerProvider {
	/** 返回本轮标签内容的异步查询，供其他数据提供器按标签键读取条目。 */
	fun contentsGetter(): CompletableFuture<TagLookup<T>>

	/** 返回当前提供器所生成标签的注册表键。 */
	fun registry(): ResourceKey<out Registry<T>>

	/** 获取指定标签的底层构建器。可在生成回调中追加原始标签条目。 */
	fun rawBuilder(key: TagKey<T>): TagBuilder

	/** 通过注册表键向标签添加条目。 */
	interface Key<T : Any> : RegistrateTagsProvider<T> {
		/** 获取指定标签的追加入口。传入注册表键即可添加条目。 */
		fun tag(key: TagKey<T>): TagAppender<ResourceKey<T>, T>
	}

	/** 通过对象添加标签条目，由提供器从对象取得对应的注册表键。 */
	interface Intrinsic<T : Any> : RegistrateTagsProvider<T> {
		/** 获取指定标签的追加入口。添加对象时由当前提供器解析其注册表键。 */
		fun tag(key: TagKey<T>): TagAppender<T, T>
	}

	/** 为指定注册表生成标签。适用于数据生成时以注册表键引用的条目。 */
	open class KeyTags<T : Any>(
		private val owner: AbstractRegistrate<*>,
		private val type: ProviderType<out KeyTags<T>>,
		private val name: String,
		output: PackOutput,
		registry: ResourceKey<out Registry<T>>,
		lookup: CompletableFuture<HolderLookup.Provider>,
	) : KeyTagProvider<T>(output, registry, lookup, owner.modid),
		Key<T> {
		/** 返回包含标签类型名称的数据生成任务名称。 */
		override fun getName(): String = "Tags ($name)"

		/** 执行登记给当前注册表标签提供器的数据生成回调。 */
		override fun addTags(provider: HolderLookup.Provider) {
			owner.genData(type, this)
		}

		/** 标签作为服务端数据生成。 */
		override fun getSide(): LogicalSide = LogicalSide.SERVER

		/** 取得或创建指定标签的底层构建器，供回调追加原始条目。 */
		override fun rawBuilder(key: TagKey<T>): TagBuilder = super.getOrCreateRawBuilder(key)

		/** 返回通过注册表键追加条目的标签构建入口。 */
		override fun tag(key: TagKey<T>): TagAppender<ResourceKey<T>, T> = super.tag(key)

		/** 异步提供包含本轮标签内容的注册表查询。 */
		override fun getFilledProvider(): CompletableFuture<HolderLookup.Provider> = createContentsProvider()

		/** 返回由此提供器处理的注册表键。 */
		override fun registry(): ResourceKey<out Registry<T>> = registryKey
	}

	/** 为指定注册表生成标签。调用方传入实际对象，由键提取器取得其注册表键。 */
	open class IntrinsicTags<T : Any>(
		private val owner: AbstractRegistrate<*>,
		private val type: ProviderType<out IntrinsicTags<T>>,
		private val name: String,
		output: PackOutput,
		registry: ResourceKey<out Registry<T>>,
		lookup: CompletableFuture<HolderLookup.Provider>,
		keyExtractor: Function<T, ResourceKey<T>>,
	) : IntrinsicHolderTagsProvider<T>(output, registry, lookup, keyExtractor, owner.modid),
		Intrinsic<T> {
		/** 返回包含标签类型名称的数据生成任务名称。 */
		override fun getName(): String = "Tags ($name)"

		/** 执行登记给当前注册表标签提供器的数据生成回调。 */
		override fun addTags(provider: HolderLookup.Provider) {
			owner.genData(type, this)
		}

		/** 标签作为服务端数据生成。 */
		override fun getSide(): LogicalSide = LogicalSide.SERVER

		/** 取得或创建指定标签的底层构建器，供回调追加原始条目。 */
		override fun rawBuilder(key: TagKey<T>): TagBuilder = super.getOrCreateRawBuilder(key)

		/** 返回通过已注册对象追加条目的标签构建入口。 */
		override fun tag(key: TagKey<T>): TagAppender<T, T> = super.tag(key)

		/** 异步提供包含本轮标签内容的注册表查询。 */
		override fun getFilledProvider(): CompletableFuture<HolderLookup.Provider> = createContentsProvider()

		/** 返回由此提供器处理的注册表键。 */
		override fun registry(): ResourceKey<out Registry<T>> = registryKey
	}
}
