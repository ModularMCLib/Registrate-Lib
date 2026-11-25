package com.modularmc.registrate.providers.generators

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.RegistrateProvider

import net.minecraft.client.data.models.BlockModelGenerators
import net.minecraft.client.data.models.ItemModelGenerators
import net.minecraft.client.data.models.ItemModelOutput
import net.minecraft.client.data.models.ModelProvider
import net.minecraft.client.renderer.item.ClientItem
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.PackOutput
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.neoforged.fml.LogicalSide

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

/** 在同一轮任务中生成方块状态和物品模型，使两个生成器共享已经登记的物品模型信息。 */
open class RegistrateModelProvider(private val parent: AbstractRegistrate<*>, output: PackOutput) :
	ModelProvider(output, parent.modid),
	RegistrateProvider {
	/** 先生成方块状态和模型，再运行物品模型回调。两步使用相同的物品模型输出记录。 */
	override fun registerModels(blockModels: BlockModelGenerators, itemModels: ItemModelGenerators) {
		val output = ItemModelCollector(itemModels.itemModelOutput)
		RegistrateBlockModelGenerator(parent, blockModels.blockStateOutput, output, blockModels.modelOutput).run()
		RegistrateItemModelGenerator(parent, output, itemModels.modelOutput).run()
	}

	/** 返回模型生成任务在日志中显示的名称。 */
	override fun getName(): String = "Blockstates and item models"

	/** 方块状态和物品模型作为客户端资源生成。 */
	override fun getSide(): LogicalSide = LogicalSide.CLIENT

	internal class ItemModelCollector(private val output: ItemModelOutput) : ItemModelOutput {
		private val models = ObjectOpenHashSet<Identifier>()

		fun contains(item: Item): Boolean = BuiltInRegistries.ITEM.getKey(item) in models

		override fun accept(item: Item, generator: ItemModel.Unbaked, properties: ClientItem.Properties) {
			output.accept(item, generator, properties)
			models.add(BuiltInRegistries.ITEM.getKey(item))
		}

		override fun register(item: Item, clientItem: ClientItem) {
			output.register(item, clientItem)
			models.add(BuiltInRegistries.ITEM.getKey(item))
		}

		override fun register(identifier: Identifier, clientItem: ClientItem) {
			output.register(identifier, clientItem)
			models.add(identifier)
		}

		override fun copy(donor: Item, acceptor: Item) {
			output.copy(donor, acceptor)
			models.add(BuiltInRegistries.ITEM.getKey(acceptor))
		}
	}
}
