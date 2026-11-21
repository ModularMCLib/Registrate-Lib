package com.modularmc.registrate.test.mod

import com.modularmc.registrate.Registrate
import com.modularmc.registrate.util.nullness.NonNullBiConsumer
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.client.data.models.model.ModelTemplates
import net.minecraft.client.data.models.model.TextureMapping
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.fluids.FluidType

/** Kotlin registration examples using the test mod's Registrate instance. */
class KotlinRegistrations(registrate: Registrate) {
	val magicItemModelTest = registrate.block("magic_item_model", ::Block)
		.blockstate {
			NonNullBiConsumer { context, models ->
				val model = ModelTemplates.CUBE_ALL.create(
					models.modLoc("block/subfolder/" + context.name),
					TextureMapping.cube(models.mcBlockTexture("gold_block")),
					models.modelOutput,
				)
				models.create(context.entry, model)
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

	class TestDummyBlockEntity(type: BlockEntityType<out TestDummyBlockEntity>, pos: BlockPos, state: BlockState) : BlockEntity(type, pos, state)
}
