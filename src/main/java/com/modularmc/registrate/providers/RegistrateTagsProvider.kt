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

/** A provider for one registry's tags. Tag builders are populated during the provider's DataGen callback. */
interface RegistrateTagsProvider<T : Any> : RegistrateLookupFillerProvider {
	fun contentsGetter(): CompletableFuture<TagLookup<T>>

	fun registry(): ResourceKey<out Registry<T>>

	fun rawBuilder(key: TagKey<T>): TagBuilder

	/** Appends entries using their registry keys. */
	interface Key<T : Any> : RegistrateTagsProvider<T> {
		fun tag(key: TagKey<T>): TagAppender<ResourceKey<T>, T>
	}

	/** Appends entries by obtaining their registry keys from the objects themselves. */
	interface Intrinsic<T : Any> : RegistrateTagsProvider<T> {
		fun tag(key: TagKey<T>): TagAppender<T, T>
	}

	open class KeyTags<T : Any>(
		private val owner: AbstractRegistrate<*>,
		private val type: ProviderType<out KeyTags<T>>,
		private val name: String,
		output: PackOutput,
		registry: ResourceKey<out Registry<T>>,
		lookup: CompletableFuture<HolderLookup.Provider>,
	) : KeyTagProvider<T>(output, registry, lookup, owner.modid),
		Key<T> {
		override fun getName(): String = "Tags ($name)"

		override fun addTags(provider: HolderLookup.Provider) {
			owner.genData(type, this)
		}

		override fun getSide(): LogicalSide = LogicalSide.SERVER

		override fun rawBuilder(key: TagKey<T>): TagBuilder = super.getOrCreateRawBuilder(key)

		override fun tag(key: TagKey<T>): TagAppender<ResourceKey<T>, T> = super.tag(key)

		override fun getFilledProvider(): CompletableFuture<HolderLookup.Provider> = createContentsProvider()

		override fun registry(): ResourceKey<out Registry<T>> = registryKey
	}

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
		override fun getName(): String = "Tags ($name)"

		override fun addTags(provider: HolderLookup.Provider) {
			owner.genData(type, this)
		}

		override fun getSide(): LogicalSide = LogicalSide.SERVER

		override fun rawBuilder(key: TagKey<T>): TagBuilder = super.getOrCreateRawBuilder(key)

		override fun tag(key: TagKey<T>): TagAppender<T, T> = super.tag(key)

		override fun getFilledProvider(): CompletableFuture<HolderLookup.Provider> = createContentsProvider()

		override fun registry(): ResourceKey<out Registry<T>> = registryKey
	}
}
