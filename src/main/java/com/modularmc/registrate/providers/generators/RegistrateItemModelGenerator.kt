package com.modularmc.registrate.providers.generators

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.PackOutput
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder
import net.neoforged.neoforge.client.model.generators.ItemModelProvider
import net.neoforged.neoforge.client.model.generators.loaders.DynamicFluidContainerModelBuilder
import net.neoforged.neoforge.common.data.ExistingFileHelper

import java.util.function.UnaryOperator

open class RegistrateItemModelGenerator(private val parent: AbstractRegistrate<*>, output: PackOutput, private val fileHelper: ExistingFileHelper) :
	ItemModelProvider(output, parent.modid, fileHelper) {
	public override fun registerModels() {
		parent.genData<RegistrateItemModelGenerator>(ProviderType.ITEM_MODEL, this)
	}

	fun hasModel(item: Item): Boolean = generatedModels.containsKey(itemLocation(item))

	fun createWithExistingModel(item: Item, model: ResourceLocation): ItemModelBuilder = withExistingParent(itemLocation(item).toString(), model)

	fun generateFlatItem(item: Item, texture: ResourceLocation): ItemModelBuilder = generateFlatItem(item, mcLoc("item/generated"), texture)

	fun generateFlatItem(item: Item, parentModel: ResourceLocation, texture: ResourceLocation): ItemModelBuilder = createWithExistingModel(item, parentModel).texture("layer0", texture)

	@JvmOverloads
	fun generateFlatBlockItem(item: BlockItem, suffix: String = ""): ItemModelBuilder = generateFlatItem(
		item,
		RegistrateBlockModelGenerator.modelLocation(item.block).withSuffix(suffix),
	)

	fun generateBlockItem(item: BlockItem, modelMapper: UnaryOperator<ResourceLocation>): ItemModelBuilder = createWithExistingModel(
		item,
		modelMapper.apply(RegistrateBlockModelGenerator.modelLocation(item.block)),
	)

	fun generateBlockItem(item: BlockItem, suffix: String): ItemModelBuilder = generateBlockItem(item) { model: ResourceLocation -> model.withSuffix(suffix) }

	fun mcBlockTexture(path: String): ResourceLocation = mcLoc("block/$path")

	fun modBlockTexture(path: String): ResourceLocation = modLoc("block/$path")

	fun mcItemTexture(path: String): ResourceLocation = mcLoc("item/$path")

	fun modItemTexture(path: String): ResourceLocation = modLoc("item/$path")

	fun modid(item: NonNullSupplier<out ItemLike>): String = BuiltInRegistries.ITEM.getKey(item.get().asItem()).namespace

	fun name(item: NonNullSupplier<out ItemLike>): String = BuiltInRegistries.ITEM.getKey(item.get().asItem()).path

	fun generateFluidBucket(item: Item, fluid: Fluid) {
		getBuilder(itemLocation(item).toString())
			.texture("particle", mcItemTexture("bucket"))
			.texture("base", mcItemTexture("bucket"))
			.texture("fluid", ResourceLocation.fromNamespaceAndPath("neoforge", "item/mask/bucket_fluid"))
			.customLoader { model, _ -> DynamicFluidContainerModelBuilder.begin(model, fileHelper) }
			.fluid(fluid)
			.flipGas(true)
			.applyFluidLuminosity(true)
			.end()
	}

	companion object {
		private fun itemLocation(item: Item): ResourceLocation = BuiltInRegistries.ITEM.getKey(item).withPrefix("item/")
	}
}
