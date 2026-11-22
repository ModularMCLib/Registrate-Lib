package com.modularmc.registratetest.gametests

import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registratetest.mod.TestMod

import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import net.minecraft.world.item.SpawnEggItem
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.registries.NeoForgeRegistries
import net.neoforged.testframework.DynamicTest
import net.neoforged.testframework.annotation.ForEachTest
import net.neoforged.testframework.annotation.TestHolder
import net.neoforged.testframework.gametest.EmptyTemplate
import net.neoforged.testframework.gametest.GameTest

@ForEachTest(groups = ["registrate-runtime"])
object RegistrationRuntimeTests {
	@JvmStatic
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(description = ["Ensure block item lang reuse and fluid optional helpers behave as expected"])
	fun ensureItemAndFluidHelpers(test: DynamicTest) {
		test.onGameTest { helper ->
			val mod = TestMod.instance()
			helper.assertValueEqual(
				mod.testblock.get().descriptionId,
				mod.testblockitem.asStack().item.descriptionId,
				"Block item should reuse the block description key",
			)
			helper.assertTrue(mod.testfluid.getBlock<Block>().isEmpty, "No block entry for the test fluid")
			helper.assertTrue(mod.testfluid.getBucket<Item>().isEmpty, "No bucket for noBucket()")
			val supplierFluid = mod.kotlinEntries.supplierFluid
			helper.assertTrue(
				mod.registrate().get<FluidType, FluidType>("test_supplier_fluid", NeoForgeRegistries.Keys.FLUID_TYPES).get() === supplierFluid.getType(),
				"Fluid type supplied through the short overload is registered",
			)
			helper.succeed()
		}
	}

	@JvmStatic
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(description = ["Ensure Java and Kotlin registrations share the test mod's Registrate instance"])
	fun ensureAuxiliaryRegistrations(test: DynamicTest) {
		test.onGameTest { helper ->
			val mod = TestMod.instance()
			val kotlinEntries = mod.kotlinEntries
			helper.assertValueEqual("testmenu", mod.testmenu.id.path, "Menu registration path")
			helper.assertValueEqual("entity.testmod.testentity", mod.testentity.get().descriptionId, "Entity translation key")
			helper.assertTrue(
				SpawnEggItem.getType(mod.testentitySpawnEgg.asStack()) === mod.testentity.get(),
				"Spawn egg should resolve to the registered entity type",
			)
			helper.assertValueEqual("testitem", mod.testduplicatename.id.path, "Duplicate-name entity registration path")
			helper.assertValueEqual("testblockentity", kotlinEntries.standaloneBlockEntity.id.path, "Standalone block entity registration path")
			helper.assertValueEqual("testcustom", mod.testcustom.id.path, "Custom registry entry path")
			helper.assertValueEqual(
				"block.testmod.magic_item_model",
				kotlinEntries.magicItemModelTest.asStack().item.descriptionId,
				"Magic item-model block description key",
			)
			helper.assertTrue(
				mod.registrate().get<Block, Block>("magic_item_model", Registries.BLOCK) === kotlinEntries.magicItemModelTest,
				"Kotlin block accessible through the Java entry point's Registrate",
			)
			helper.succeed()
		}
	}

	@JvmStatic
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(description = ["Reject duplicate registrations before replacing the original holder and its declarations"])
	fun rejectDuplicateItemWithoutReplacingMetadata(test: DynamicTest) {
		test.onGameTest { helper ->
			val mod = TestMod.instance()
			val metadata = mod.testitem.metadata()
			try {
				mod.registrate().item("testitem", ::Item).lang("Duplicate").register()
				throw AssertionError("Duplicate registration succeeded")
			} catch (error: IllegalArgumentException) {
				val message = error.message!!
				helper.assertTrue("testmod:testitem" in message && "minecraft:item" in message, "Duplicate diagnostic: $message")
			}

			helper.assertTrue(mod.registrate().get<Item, Item>("testitem", Registries.ITEM) === mod.testitem, "Original holder")
			helper.assertTrue(mod.testitem.metadata() === metadata, "Original entry metadata")
			helper.assertTrue(metadata.dataGenerators().any { it.type === ProviderType.ITEM_MODEL }, "Item model declaration")
			helper.assertTrue(mod.testcustom.metadata().dataGenerators().isEmpty(), "Custom registry metadata")
			helper.succeed()
		}
	}
}
