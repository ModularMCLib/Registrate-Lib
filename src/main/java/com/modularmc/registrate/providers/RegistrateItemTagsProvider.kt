package com.modularmc.registrate.providers

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.RegistrateTagsProvider.IntrinsicTags
import com.modularmc.registrate.providers.core.ProviderType

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.PackOutput
import net.minecraft.data.tags.TagsProvider.TagLookup
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.common.data.ExistingFileHelper

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

import java.util.concurrent.CompletableFuture

open class RegistrateItemTagsProvider(
	owner: AbstractRegistrate<*>,
	type: ProviderType<RegistrateItemTagsProvider>,
	name: String,
	output: PackOutput,
	registriesLookup: CompletableFuture<HolderLookup.Provider>,
	private val blockTags: CompletableFuture<TagLookup<Block>>,
	fileHelper: ExistingFileHelper,
) : IntrinsicTags<Item>(owner, type, name, output, Registries.ITEM, registriesLookup, { item ->
	BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow { IllegalStateException("Cannot generate tags for unregistered item: $item") }
}, fileHelper) {
	private val tagsToCopy = Object2ObjectOpenHashMap<TagKey<Block>, TagKey<Item>>()

	open fun copy(sourceTag: TagKey<Block>, targetTag: TagKey<Item>) {
		tagsToCopy[sourceTag] = targetTag
	}

	override fun createContentsProvider(): CompletableFuture<HolderLookup.Provider> = super.createContentsProvider().thenCombineAsync(blockTags) { lookup, blockTagLookup ->
		for ((source, target) in tagsToCopy) {
			val destination = getOrCreateRawBuilder(target)
			blockTagLookup.apply(source).orElseThrow { IllegalStateException("Missing block tag ${source.location()}") }
				.build().forEach(destination::add)
		}
		lookup
	}
}
