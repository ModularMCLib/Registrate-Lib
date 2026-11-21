package com.modularmc.registrate.providers.generators

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.ProviderType

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.PackOutput
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.client.model.generators.BlockStateProvider
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder
import net.neoforged.neoforge.client.model.generators.VariantBlockStateBuilder
import net.neoforged.neoforge.common.data.ExistingFileHelper

import com.google.gson.JsonElement

import java.util.Optional

open class RegistrateBlockModelGenerator(private val parent: AbstractRegistrate<*>, output: PackOutput, fileHelper: ExistingFileHelper) : BlockStateProvider(output, parent.modid, fileHelper) {
	private val itemModels: RegistrateItemModelGenerator = RegistrateItemModelGenerator(parent, output, fileHelper)

	override fun registerStatesAndModels() {
		parent.genData<RegistrateBlockModelGenerator>(ProviderType.BLOCKSTATE, this)
		itemModels.registerModels()
	}

	override fun itemModels(): RegistrateItemModelGenerator = itemModels

	fun create(block: Block, model: ResourceLocation) {
		simpleBlock(block, models().getExistingFile(model))
	}

	fun createTrivialCube(block: Block) {
		simpleBlock(block, models().cubeAll(modelLocation(block).path, blockTexture(block)))
	}

	fun mcBlockTexture(path: String): ResourceLocation = mcLoc("block/$path")

	fun modBlockTexture(path: String): ResourceLocation = modLoc("block/$path")

	fun blockTexture(block: Block, suffix: String): ResourceLocation = blockTexture(block).withSuffix(suffix)

	open fun getExistingVariantBuilder(block: Block): Optional<VariantBlockStateBuilder> = Optional.ofNullable(registeredBlocks[block] as? VariantBlockStateBuilder)

	open fun getExistingMultipartBuilder(block: Block): Optional<MultiPartBlockStateBuilder> = Optional.ofNullable(registeredBlocks[block] as? MultiPartBlockStateBuilder)

	open fun getSingleVariantModel(block: Block): Optional<ResourceLocation> = getExistingVariantBuilder(block)
		.map<JsonElement> { it.models[it.partialState()]?.toJSON() }
		.filter(JsonElement::isJsonObject)
		.map { it.asJsonObject.get("model") }
		.map { ResourceLocation.parse(it.asString) }

	companion object {
		@JvmStatic
		fun modelLocation(block: Block): ResourceLocation = BuiltInRegistries.BLOCK.getKey(block).withPrefix("block/")
	}
}
