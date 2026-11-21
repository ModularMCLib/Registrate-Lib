package com.modularmc.registrate.providers.generators

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.data.models.ItemModelGenerators
import net.minecraft.client.data.models.ItemModelOutput
import net.minecraft.client.data.models.model.*
import net.minecraft.client.resources.model.sprite.Material
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.client.model.item.DynamicFluidContainerModel
import net.neoforged.neoforge.client.model.item.DynamicFluidContainerModel.Textures

import java.util.*
import java.util.function.BiConsumer
import java.util.function.UnaryOperator

open class RegistrateItemModelGenerator(private val parent: AbstractRegistrate<*>, private val output: ItemModelOutput, model: BiConsumer<Identifier, ModelInstance>) :
	ItemModelGenerators(output, model) {
	override fun run() {
		parent.genData(ProviderType.ITEM_MODEL, this)
	}

	fun hasModel(item: Item): Boolean = (output as? RegistrateModelProvider.ItemModelCollector)?.contains(item) == true

	fun createWithExistingModel(item: Item, id: Identifier) {
		itemModelOutput.accept(item, ItemModelUtils.plainModel(id))
	}

	fun generateWithTemplate(item: Item, template: ModelTemplate, textures: TextureMapping) {
		itemModelOutput.accept(item, ItemModelUtils.plainModel(template.create(item, textures, modelOutput)))
	}

	fun generateFlatItem(item: Item, layer0: Material) {
		generateFlatItem(item, ModelTemplates.FLAT_ITEM, layer0)
	}

	fun generateFlatItem(item: Item, template: ModelTemplate, layer0: Material) {
		itemModelOutput.accept(
			item,
			ItemModelUtils.plainModel(template.create(item, TextureMapping.layer0(layer0), modelOutput)),
		)
	}

	fun generateFlatItem(item: Item, template: ModelTemplate, textures: TextureMapping, vararg tints: ItemTintSource) {
		val model = template.create(item, textures, modelOutput)
		itemModelOutput.accept(
			item,
			if (tints.isEmpty()) ItemModelUtils.plainModel(model) else ItemModelUtils.tintedModel(model, *tints),
		)
	}

	fun generateFlatBlockItem(item: BlockItem) {
		generateFlatItem(item, TextureMapping.getBlockTexture(item.getBlock()))
	}

	fun generateFlatBlockItem(item: BlockItem, suffix: String) {
		generateFlatItem(item, TextureMapping.getBlockTexture(item.getBlock(), suffix))
	}

	fun generateBlockItem(item: BlockItem, modelMapper: UnaryOperator<Identifier>) {
		itemModelOutput.accept(
			item,
			ItemModelUtils.plainModel(modelMapper.apply(ModelLocationUtils.getModelLocation(item.getBlock()))),
		)
	}

	fun generateBlockItem(item: BlockItem, suffix: String) {
		generateBlockItem(item, UnaryOperator { model: Identifier -> model.withSuffix(suffix) })
	}

	fun mcLoc(id: String): Identifier = Identifier.withDefaultNamespace(id)

	fun modLoc(id: String): Identifier = Identifier.fromNamespaceAndPath(parent.modid, id)

	fun mcBlockTexture(path: String): Material = Material(mcLoc("block/" + path))

	fun modBlockTexture(path: String): Material = Material(modLoc("block/" + path))

	fun mcItemTexture(path: String): Material = Material(mcLoc("item/" + path))

	fun modItemTexture(path: String): Material = Material(modLoc("item/" + path))

	fun modid(item: NonNullSupplier<out ItemLike>): String = BuiltInRegistries.ITEM.getKey(item.get().asItem()).getNamespace()

	fun name(item: NonNullSupplier<out ItemLike>): String = BuiltInRegistries.ITEM.getKey(item.get().asItem()).getPath()

	fun generateTintedModel(entry: Item, model: Identifier, tint: ItemTintSource) {
		this.itemModelOutput.accept(entry, ItemModelUtils.tintedModel(model, tint))
	}

	fun generateFluidBucket(item: Item, fluid: Fluid) {
		itemModelOutput.accept(
			item,
			DynamicFluidContainerModel.Unbaked(
				Textures(
					Optional.of<Material>(mcItemTexture("bucket")),
					Optional.of<Material>(mcItemTexture("bucket")),
					Optional.of<Material>(
						Material(
							Identifier.fromNamespaceAndPath(
								"neoforge",
								"item/mask/bucket_fluid",
							),
						),
					),
					Optional.empty<Material>(),
				),
				fluid,
				true,
				true,
				true,
			),
		)
	}
}
