package com.modularmc.registrate.test.gametests;

import com.modularmc.registrate.providers.core.ProviderType;
import com.modularmc.registrate.test.mod.TestMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.Item;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@ForEachTest(groups = "registrate-runtime")
@SuppressWarnings("unused")
public class RegistrationRuntimeTests {

    @GameTest
    @EmptyTemplate(floor = true)
    @TestHolder(description = "Ensure block item lang reuse and fluid optional helpers behave as expected")
    public static void ensureItemAndFluidHelpers(final DynamicTest test) {
        test.onGameTest(helper -> helper.startSequence(() -> TestMod.instance().testblockitem.asStack().getItem().getDescriptionId())
                .thenExecute(blockItemDescriptionId -> {
                    helper.assertValueEqual(TestMod.instance().testblock.get().getDescriptionId(), blockItemDescriptionId, "Block item should reuse the block description key");
                    helper.assertValueEqual(true, TestMod.instance().testfluid.getBlock().isEmpty(), "Fluid entry should return an empty block optional when no sibling exists");
                    helper.assertValueEqual(true, TestMod.instance().testfluid.getBucket().isEmpty(), "Fluid entry should hide buckets when noBucket() is used");
                })
                .thenSucceed());
    }

    @GameTest
    @EmptyTemplate(floor = true)
    @TestHolder(description = "Ensure auxiliary registrations remain available at runtime")
    public static void ensureAuxiliaryRegistrations(final DynamicTest test) {
        test.onGameTest(helper -> helper.startSequence(() -> TestMod.instance().testmenu.getId().getPath())
                .thenExecute(menuId -> {
                    helper.assertValueEqual("testmenu", menuId, "Menu registration path");
                    helper.assertValueEqual("entity.testmod.testentity", TestMod.instance().testentity.get().getDescriptionId(), "Entity translation key");
                    helper.assertValueEqual(TestMod.instance().testentity.get(), TestMod.instance().testentitySpawnEgg.get().getType(TestMod.instance().testentitySpawnEgg.asStack()), "Spawn egg should resolve to the registered entity type");
                    helper.assertValueEqual("testitem", TestMod.instance().testduplicatename.getId().getPath(), "Duplicate-name entity registration path");
                    helper.assertValueEqual("testblockentity", TestMod.instance().testblockentity.getId().getPath(), "Standalone block entity registration path");
                    helper.assertValueEqual("testcustom", TestMod.instance().testcustom.getId().getPath(), "Custom registry entry path");
                    helper.assertValueEqual("block.testmod.magic_item_model", TestMod.instance().magicItemModelTest.asStack().getItem().getDescriptionId(), "Magic item-model block description key");
                })
                .thenSucceed());
    }

    @GameTest
    @EmptyTemplate(floor = true)
    @TestHolder(description = "Reject duplicate registrations before replacing the original holder and its declarations")
    public static void rejectDuplicateItemWithoutReplacingMetadata(final DynamicTest test) {
        test.onGameTest(helper -> {
            var mod = TestMod.instance();
            var metadata = mod.testitem.metadata();
            try {
                mod.registrate().item("testitem", Item::new).lang("Duplicate").register();
                throw new AssertionError("Duplicate registration succeeded");
            } catch (IllegalArgumentException error) {
                helper.assertTrue(error.getMessage().contains("testmod:testitem") && error.getMessage().contains("minecraft:item"), "Duplicate diagnostic: " + error.getMessage());
            }

            helper.assertTrue(mod.registrate().get("testitem", Registries.ITEM) == mod.testitem, "Original holder");
            helper.assertTrue(mod.testitem.metadata() == metadata, "Original metadata snapshot");
            helper.assertTrue(metadata.dataGenerators().stream().anyMatch(generator -> generator.type() == ProviderType.ITEM_MODEL), "Item model declaration");
            helper.assertTrue(mod.testcustom.metadata().dataGenerators().isEmpty(), "Custom registry metadata");
            helper.succeed();
        });
    }
}
