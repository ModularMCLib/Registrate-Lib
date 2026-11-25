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

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

import java.util.concurrent.CompletableFuture

/** 为已注册物品生成标签，可从本轮生成的方块标签复制条目。 */
open class RegistrateItemTagsProvider(
	owner: AbstractRegistrate<*>,
	type: ProviderType<RegistrateItemTagsProvider>,
	name: String,
	output: PackOutput,
	registriesLookup: CompletableFuture<HolderLookup.Provider>,
	private val blockTags: CompletableFuture<TagLookup<Block>>,
) : IntrinsicTags<Item>(owner, type, name, output, Registries.ITEM, registriesLookup, { item ->
	BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow { IllegalStateException("Cannot generate tags for unregistered item: $item") }
}) {
	private val tagsToCopy = Object2ObjectOpenHashMap<TagKey<Block>, TagKey<Item>>()

	/** 将 [sourceTag] 的方块标签条目复制到 [targetTag]。再次配置相同来源时采用最后指定的目标。 */
	open fun copy(sourceTag: TagKey<Block>, targetTag: TagKey<Item>) {
		tagsToCopy[sourceTag] = targetTag
	}

	/** 在物品标签内容生成后合并要复制的方块标签。找不到指定来源时，生成任务异常结束。 */
	override fun createContentsProvider(): CompletableFuture<HolderLookup.Provider> = super.createContentsProvider().thenCombineAsync(blockTags) { lookup, blockTagLookup ->
		for ((source, target) in tagsToCopy) {
			val destination = getOrCreateRawBuilder(target)
			blockTagLookup.apply(source).orElseThrow { IllegalStateException("Missing block tag ${source.location()}") }
				.build().forEach(destination::add)
		}
		lookup
	}
}
