package com.modularmc.registratetest.mod

import com.modularmc.registrate.Registrate
import com.modularmc.registrate.util.nullness.NonNullBiConsumer
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.fluids.FluidType

/** 示例模组的 Kotlin 注册用法，复用模组已有的 Registrate 实例。 */
class KotlinRegistrations(registrate: Registrate) {
	val magicItemModelTest = registrate.block("magic_item_model", ::Block)
		.blockstate {
			NonNullBiConsumer { context, models ->
				val model = models.models().cubeAll("block/subfolder/" + context.name, models.mcBlockTexture("gold_block"))
				models.create(context.entry, model.location)
			}
		}
		.simpleItem()
		.register()

	val standaloneBlockEntity = registrate.blockEntity("testblockentity", ::TestDummyBlockEntity)
		.validBlock { Blocks.DIRT }
		.register()

	val supplierFluid = registrate.fluid("test_supplier_fluid", NonNullSupplier { FluidType(FluidType.Properties.create()) })
		.noBlock()
		.noBucket()
		.register()

	/** 示例方块实体，用于覆盖 Kotlin 构建链对方块实体类型的注册。 */
	class TestDummyBlockEntity(type: BlockEntityType<out TestDummyBlockEntity>, pos: BlockPos, state: BlockState) : BlockEntity(type, pos, state)
}
