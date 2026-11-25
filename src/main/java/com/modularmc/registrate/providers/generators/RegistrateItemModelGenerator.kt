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

/** 执行模组的物品模型回调，将生成结果送入当前模型提供器。 */
open class RegistrateItemModelGenerator(private val parent: AbstractRegistrate<*>, private val output: ItemModelOutput, model: BiConsumer<Identifier, ModelInstance>) :
	ItemModelGenerators(output, model) {
	/** 运行所属模组为物品模型登记的数据生成回调。 */
	override fun run() {
		parent.genData(ProviderType.ITEM_MODEL, this)
	}

	/** 查询当前模型输出是否已处理该物品。可用于避免重复生成方块物品模型。 */
	fun hasModel(item: Item): Boolean = (output as? RegistrateModelProvider.ItemModelCollector)?.contains(item) == true

	/** 为物品登记对既有模型 [id] 的引用。 */
	fun createWithExistingModel(item: Item, id: Identifier) {
		itemModelOutput.accept(item, ItemModelUtils.plainModel(id))
	}

	/** 应用纹理映射写出模板模型，并将它绑定为物品模型。 */
	fun generateWithTemplate(item: Item, template: ModelTemplate, textures: TextureMapping) {
		itemModelOutput.accept(item, ItemModelUtils.plainModel(template.create(item, textures, modelOutput)))
	}

	/** 将 [layer0] 作为首层纹理，使用默认平面物品模板生成模型。 */
	fun generateFlatItem(item: Item, layer0: Material) {
		generateFlatItem(item, ModelTemplates.FLAT_ITEM, layer0)
	}

	/** 将 [layer0] 填入指定模板的首层纹理位，生成平面物品模型。 */
	fun generateFlatItem(item: Item, template: ModelTemplate, layer0: Material) {
		itemModelOutput.accept(
			item,
			ItemModelUtils.plainModel(template.create(item, TextureMapping.layer0(layer0), modelOutput)),
		)
	}

	/** 以指定模板和纹理生成平面物品模型。提供 [tints] 时同时登记各层着色来源。 */
	fun generateFlatItem(item: Item, template: ModelTemplate, textures: TextureMapping, vararg tints: ItemTintSource) {
		val model = template.create(item, textures, modelOutput)
		itemModelOutput.accept(
			item,
			if (tints.isEmpty()) ItemModelUtils.plainModel(model) else ItemModelUtils.tintedModel(model, *tints),
		)
	}

	/** 使用关联方块的默认纹理生成平面物品模型。 */
	fun generateFlatBlockItem(item: BlockItem) {
		generateFlatItem(item, TextureMapping.getBlockTexture(item.getBlock()))
	}

	/** 在关联方块的纹理路径后追加 [suffix]，并用该纹理生成平面物品模型。 */
	fun generateFlatBlockItem(item: BlockItem, suffix: String) {
		generateFlatItem(item, TextureMapping.getBlockTexture(item.getBlock(), suffix))
	}

	/** 先由 [modelMapper] 变换关联方块的模型标识，再将结果绑定为方块物品模型。 */
	fun generateBlockItem(item: BlockItem, modelMapper: UnaryOperator<Identifier>) {
		itemModelOutput.accept(
			item,
			ItemModelUtils.plainModel(modelMapper.apply(ModelLocationUtils.getModelLocation(item.getBlock()))),
		)
	}

	/** 引用关联方块模型路径追加 [suffix] 后的模型，用作方块物品模型。 */
	fun generateBlockItem(item: BlockItem, suffix: String) {
		generateBlockItem(item, UnaryOperator { model: Identifier -> model.withSuffix(suffix) })
	}

	/** 使用 Minecraft 命名空间和给定路径构造资源标识。 */
	fun mcLoc(id: String): Identifier = Identifier.withDefaultNamespace(id)

	/** 使用当前模组命名空间和给定路径构造资源标识。 */
	fun modLoc(id: String): Identifier = Identifier.fromNamespaceAndPath(parent.modid, id)

	/** 引用 Minecraft 命名空间下 `textures/block` 中的纹理。 */
	fun mcBlockTexture(path: String): Material = Material(mcLoc("block/" + path))

	/** 引用当前模组 `textures/block` 中的纹理。 */
	fun modBlockTexture(path: String): Material = Material(modLoc("block/" + path))

	/** 引用 Minecraft 命名空间下 `textures/item` 中的纹理。 */
	fun mcItemTexture(path: String): Material = Material(mcLoc("item/" + path))

	/** 引用当前模组 `textures/item` 中的纹理。 */
	fun modItemTexture(path: String): Material = Material(modLoc("item/" + path))

	/** 返回物品注册标识的命名空间。取值器应提供已绑定的物品或其关联对象。 */
	fun modid(item: NonNullSupplier<out ItemLike>): String = BuiltInRegistries.ITEM.getKey(item.get().asItem()).getNamespace()

	/** 返回物品注册标识的路径。取值器应提供已绑定的物品或其关联对象。 */
	fun name(item: NonNullSupplier<out ItemLike>): String = BuiltInRegistries.ITEM.getKey(item.get().asItem()).getPath()

	/** 引用已有模型，并附加该物品的着色来源。 */
	fun generateTintedModel(entry: Item, model: Identifier, tint: ItemTintSource) {
		this.itemModelOutput.accept(entry, ItemModelUtils.tintedModel(model, tint))
	}

	/** 用默认桶纹理和流体遮罩生成动态桶模型，流体外观由 [fluid] 决定。 */
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
