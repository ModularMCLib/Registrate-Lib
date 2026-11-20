package com.modularmc.registrate.test.mod;

import com.modularmc.registrate.Registrate;
import com.modularmc.registrate.builders.BlockBuilder;
import com.modularmc.registrate.providers.core.ProviderType;
import com.modularmc.registrate.util.DataIngredient;
import com.modularmc.registrate.util.entry.*;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.RegistryBuilder;
import net.neoforged.testframework.conf.ClientConfiguration;
import net.neoforged.testframework.conf.Feature;
import net.neoforged.testframework.conf.FrameworkConfiguration;
import net.neoforged.testframework.impl.MutableTestFramework;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

@Mod(TestMod.MOD_ID)
public class TestMod {

    public static final String MOD_ID = "testmod";

    public static final class TestBlock extends Block implements EntityBlock {

        public TestBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(BlockStateProperties.CHEST_TYPE);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level worldIn, BlockPos pos, Player player, BlockHitResult hit) {
            if (!worldIn.isClientSide()) {
                player.openMenu(new MenuProvider() {

                    @Override
                    public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
                        return new ChestMenu(MenuType.GENERIC_9x3, windowId, inv, TestMod.instance().testblockbe.get(worldIn, pos).orElseThrow(IllegalStateException::new), 3);
                    }

                    @Override
                    public Component getDisplayName() {
                        return Component.literal("Test");
                    }
                });
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return TestMod.instance().testblockbe.create(pos, state);
        }
    }

    private static class TestBlockEntity extends ChestBlockEntity {

        public TestBlockEntity(BlockEntityType<? extends TestBlockEntity> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }
    }

    private static class TestBlockEntityRenderer implements BlockEntityRenderer<TestBlockEntity> {

        private final ItemRenderer itemRenderer;

        public TestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
            itemRenderer = context.getItemRenderer();
        }

        @Override
        public void render(TestBlockEntity blockEntity, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            itemRenderer.renderStatic(new ItemStack(Items.DIAMOND), ItemDisplayContext.GROUND,
                    light, overlay, poseStack, buffers, blockEntity.getLevel(), 0);
            poseStack.popPose();
        }
    }

    public static final class TestDummyBlockEntity extends BlockEntity {

        public TestDummyBlockEntity(BlockEntityType<? extends TestDummyBlockEntity> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }
    }

    public static final class TestEntity extends Pig {

        public TestEntity(EntityType<? extends Pig> p_i50250_1_, Level p_i50250_2_) {
            super(p_i50250_1_, p_i50250_2_);
        }
    }

    public static final class TestCustomRegistryEntry {}

    private final Registrate registrate = Registrate.create("testmod");

    @VisibleForTesting
    public final RegistryEntry<CreativeModeTab, CreativeModeTab> testcreativetab = registrate.object("test_creative_mode_tab")
            .defaultCreativeTab(tab -> tab.withLabelColor(0xFF00AA00))
            .register();

    private final AtomicBoolean sawCallback = new AtomicBoolean();

    @VisibleForTesting
    public final ItemEntry<Item> testitem = registrate.object("testitem")
            .item(Item::new)
            .onRegister(ignored -> sawCallback.set(true))
            .properties(p -> p.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.2f).build()))
            .tag(ItemTags.BEDS)
            .model(() -> (ctx, prov) -> prov.createWithExistingModel(ctx.getEntry(), prov.mcLoc("block/stone")))
            .tab(testcreativetab.getKey(), (ctx, modifier) -> modifier.accept(ctx))
            .register();

    @VisibleForTesting
    public final EntityEntry<TestEntity> testduplicatename = registrate.object("testitem")
            .entity(TestEntity::new, MobCategory.CREATURE)
            .attributes(Pig::createAttributes)
            .loot((tb, e) -> tb.add(e, LootTable.lootTable()))
            .renderer(() -> PigRenderer::new)
            .register();

    @VisibleForTesting
    public final BlockEntry<TestBlock> testblock = registrate.object("testblock")
            .block(TestBlock::new)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .blockstate(() -> (ctx, prov) -> prov.create(ctx.getEntry(), prov.mcLoc("block/glass")))
            .transform(TestMod::applyDiamondDrop)
            .recipe((ctx, prov) -> {
                prov.shaped(RecipeCategory.MISC, ctx.getEntry())
                        .pattern("DDD").pattern("DED").pattern("DDD")
                        .define('D', Items.DIAMOND)
                        .define('E', Items.EGG)
                        .unlockedBy("has_egg", prov.has(Items.EGG))
                        .save(prov);

                prov.food(DataIngredient.items(ctx), RecipeCategory.MISC, () -> Blocks.DIAMOND_BLOCK, 1f);
            })
            .tag(BlockTags.BAMBOO_PLANTABLE_ON, BlockTags.DRAGON_IMMUNE)
            .tag(BlockTags.WITHER_IMMUNE)
            .color(() -> () -> (state, level, pos, tintIndex) -> 0xFFFF0000)
            .item()
            .model(() -> (ctx, prov) -> prov.createWithExistingModel(ctx.get(), prov.mcLoc("item/egg")))
            .color(() -> () -> (stack, tintIndex) -> 0xFFFF0000)
            .build()
            .blockEntity(TestBlockEntity::new)
            .renderer(() -> TestBlockEntityRenderer::new)
            .build()
            .register();

    @VisibleForTesting
    public final BlockEntry<Block> magicItemModelTest = registrate.object("magic_item_model")
            .block(Block::new)
            .blockstate(() -> (ctx, prov) -> prov.create(
                    ctx.getEntry(),
                    prov.models().cubeAll("block/subfolder/" + ctx.getName(), prov.mcBlockTexture("gold_block")).getLocation()))
            .simpleItem()
            .register();

    @VisibleForTesting
    public final ItemEntry<BlockItem> testblockitem = (ItemEntry<BlockItem>) testblock.<Item, BlockItem>getSibling(Registries.ITEM);
    @VisibleForTesting
    public final BlockEntityEntry<ChestBlockEntity> testblockbe = BlockEntityEntry.cast(testblock.getSibling(Registries.BLOCK_ENTITY_TYPE));

    @VisibleForTesting
    public final EntityEntry<TestEntity> testentity = registrate.object("testentity")
            .entity(TestEntity::new, MobCategory.CREATURE)
            .attributes(Pig::createAttributes)
            .renderer(() -> PigRenderer::new)
            .spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR)
            .spawnEgg(egg -> egg.model(() -> (ctx, prov) -> prov.generateFlatItem(ctx.getEntry(), prov.modItemTexture(ctx.getName()))))
            .loot((prov, type) -> prov.add(type, LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(Items.DIAMOND)
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 3)))
                                    .apply(EnchantedCountIncreaseFunction.lootingMultiplier(prov.getRegistries(), UniformGenerator.between(0, 2)))))))
            .tag(EntityTypeTags.RAIDERS)
            .register();
    @VisibleForTesting
    public final ItemEntry<SpawnEggItem> testentitySpawnEgg = ItemEntry.cast(registrate.get("testentity_spawn_egg", Registries.ITEM));

    @VisibleForTesting
    public final BlockEntityEntry<TestDummyBlockEntity> testblockentity = registrate.object("testblockentity")
            .blockEntity(TestDummyBlockEntity::new)
            .validBlock(() -> Blocks.DIRT)
            .register();

    @VisibleForTesting
    public final FluidEntry<BaseFlowingFluid.Flowing> testfluid = registrate.object("testfluid")
            .fluid(
                    ResourceLocation.withDefaultNamespace("block/water_flow"),
                    ResourceLocation.withDefaultNamespace("block/lava_still"),
                    FluidType::new)
            .properties(p -> p.lightLevel(15).canConvertToSource(true))
            .noBucket()
            .register();

    @VisibleForTesting
    public final FluidEntry<BaseFlowingFluid.Flowing> testbucketfluid = registrate.object("testbucketfluid")
            .fluid(
                    ResourceLocation.withDefaultNamespace("block/water_still"),
                    ResourceLocation.withDefaultNamespace("block/water_flow"),
                    FluidType::new)
            .properties(p -> p.lightLevel(7).canConvertToSource(true))
            .register();

    @VisibleForTesting
    public final MenuEntry<ChestMenu> testmenu = registrate.object("testmenu")
            .menu((type, windowId, inv) -> new ChestMenu(type, windowId, inv, new SimpleContainer(9 * 9), 9), () -> ContainerScreen::new)
            .register();

    @VisibleForTesting
    public final ResourceKey<Registry<TestCustomRegistryEntry>> CUSTOM_REGISTRY = registrate.makeRegistry("custom", RegistryBuilder::new);
    @VisibleForTesting
    public final RegistryEntry<TestCustomRegistryEntry, TestCustomRegistryEntry> testcustom = registrate.object("testcustom")
            .simple(CUSTOM_REGISTRY, TestCustomRegistryEntry::new);

    private static <T extends Block, P> BlockBuilder<T, P> applyDiamondDrop(BlockBuilder<T, P> builder) {
        return builder.loot((prov, block) -> prov.dropOther(block, Items.DIAMOND));
    }

    private static @Nullable TestMod INSTANCE = null;

    public TestMod(IEventBus eventBus, ModContainer container) {
        INSTANCE = this;

        registrate.addRawLang("testmod.custom.lang", "Test");
        registrate.addRawLang("testmod.custom.lang.with_placeholders1", "Placeholder 1 %s Placeholder 2 %s");
        registrate.addRawLang("testmod.custom.lang.with_placeholders2", "Placeholder 1 %s Placeholder 2 %2$s Placeholder 3 %s");
        registrate.addRawLang("testmod.custom.lang.brackets", "(Bracket 1) [Bracket 2] {Bracket 3} <Bracket 4> ◁Bracket 5▷");
        registrate.addRawLang("testmod.custom.lang.slashes", "/commmands look good and here is a backslash \\");
        registrate.addLang("tooltip", testblock.getId(), "Egg.");
        registrate.addLang("item", testitem.getId(), "testextra", "Magic!");
        registrate.addDataGenerator(ProviderType.ADVANCEMENT, adv -> Advancement.Builder.advancement()
                .addCriterion("has_egg", InventoryChangeTrigger.TriggerInstance.hasItems(Items.EGG))
                .display(Items.EGG,
                        adv.title(registrate.getModid(), "root", "Test Advancement"), adv.desc(registrate.getModid(), "root", "Get an egg."),
                        ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png"), AdvancementType.TASK, true, true, false)
                .save(adv, registrate.getModid() + ":root"));
        registrate.addDataGenerator(ProviderType.GENERIC_SERVER, provider -> provider.add(data -> {
            // generic server side provider to generate custom dimension
            // to teleport to this dimension use the following command
            // /execute as @s in testmod:test_dimension run tp @s 0 64 0
            // you can validate you are in this dimension by checking the debug screen
            // right underneath the `Chunks[C]` and `Chunk[S]` should be the dimension name
            var testDimensionTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.fromNamespaceAndPath("testmod", "test_dimension_type"));

            return new DatapackBuiltinEntriesProvider(
                    data.output(),
                    data.registries(),
                    new RegistrySetBuilder()
                            // custom dimension type, just a simple overworld-like dimension
                            .add(Registries.DIMENSION_TYPE, context -> context.register(
                                    testDimensionTypeKey,
                                    new DimensionType(
                                            OptionalLong.empty(), true, false, false, true, 1D, true, false,
                                            -64, 384, 384, BlockTags.INFINIBURN_OVERWORLD,
                                            BuiltinDimensionTypes.OVERWORLD_EFFECTS, 0F,
                                            new DimensionType.MonsterSettings(false, true, UniformInt.of(0, 7), 0))))
                            // register custom dimension for the dimension type
                            // simple single biome (plains) dimension
                            .add(Registries.LEVEL_STEM, context -> {
                                var plains = context.lookup(Registries.BIOME).getOrThrow(Biomes.PLAINS);
                                var testDimensionType = context.lookup(Registries.DIMENSION_TYPE).getOrThrow(testDimensionTypeKey);
                                var overworldNoiseSettings = context.lookup(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);

                                context.register(
                                        ResourceKey.create(Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath("testmod", "test_dimension")),
                                        new LevelStem(
                                                testDimensionType,
                                                new NoiseBasedChunkGenerator(
                                                        new FixedBiomeSource(plains),
                                                        overworldNoiseSettings)));
                            }),
                    Set.of("testmod"));
        }));

        eventBus.addListener(this::onCommonSetup);

        // Setup gametests for normal run config
        final MutableTestFramework framework = FrameworkConfiguration
                .builder(ResourceLocation.fromNamespaceAndPath(MOD_ID, "tests"))
                .clientConfiguration(() -> ClientConfiguration.builder()
                        .toggleOverlayKey(GLFW.GLFW_KEY_O)
                        .openManagerKey(GLFW.GLFW_KEY_M)
                        .build())
                .enable(Feature.CLIENT_SYNC, Feature.TEST_STORE)
                .build()
                .create();

        framework.init(eventBus, container);

        NeoForge.EVENT_BUS.addListener((final RegisterCommandsEvent event) -> {
            final LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("tests");
            framework.registerCommands(node);
            event.getDispatcher().register(node);
        });
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        if (!sawCallback.get()) {
            throw new IllegalStateException("Register callback not fired!");
        }

        testitem.is(Items.SNOWBALL);
        testblockitem.is(Items.STONE);
        testblockbe.is(BlockEntityType.CHEST);
        // testbiome.is(Feature.BAMBOO); // should not compile
        if (testfluid.getBucket().isPresent()) throw new IllegalStateException("Expected no bucket entry for test fluid");
        if (testfluid.getSiblingOptional(Registries.ITEM).isPresent()) throw new IllegalStateException("Expected no item sibling for test fluid");
        if (testfluid.getBlock().isPresent()) throw new IllegalStateException("Expected no block sibling for test fluid");
        if (testfluid.getSource().getSource() != testfluid.getSource()) throw new IllegalStateException("Expected canonical fluid source");
        if (testbucketfluid.getBucket().isEmpty()) throw new IllegalStateException("Expected a generated bucket entry for test bucket fluid");
    }

    public static TestMod instance() {
        Objects.requireNonNull(INSTANCE, "Attempting to get mod instance before mod construction");
        return INSTANCE;
    }
}
