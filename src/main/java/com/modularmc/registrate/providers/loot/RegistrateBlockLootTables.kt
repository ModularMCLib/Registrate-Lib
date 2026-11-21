// Preserve Java's public static bridges to inherited protected static methods.
@file:Suppress("ACCIDENTAL_OVERRIDE")

package com.modularmc.registrate.providers.loot

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.data.loot.BlockLootSubProvider
import net.minecraft.world.entity.EntityType
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.Item
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider

import java.util.function.Consumer
import javax.annotation.processing.Generated

open class RegistrateBlockLootTables(provider: HolderLookup.Provider, private val parent: AbstractRegistrate<*>, private val callback: Consumer<RegistrateBlockLootTables>) :
	BlockLootSubProvider(mutableSetOf<Item>(), FeatureFlags.REGISTRY.allFlags(), provider),
	RegistrateLootTables {
	private val itemLookup = registries.lookupOrThrow(Registries.ITEM)
	private val blockLookup = registries.lookupOrThrow(Registries.BLOCK)
	private val entityLookup = registries.lookupOrThrow(Registries.ENTITY_TYPE)

	override fun generate() {
		callback.accept(this)
	}

	override fun getKnownBlocks(): Iterable<Block> = parent.getAll<Block, Block>(Registries.BLOCK).map { it.get() }

	fun getRegistries(): HolderLookup.Provider = registries

	fun itemLookup(): HolderLookup<Item> = itemLookup

	fun blockLookup(): HolderLookup<Block> = blockLookup

	fun entityLookup(): HolderLookup<EntityType<*>> = entityLookup

	// @formatter:off
	// GENERATED OVERRIDE START

	/** Generated override to expose protected method: [BlockLootSubProvider.applyExplosionDecay]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun <T : FunctionUserBuilder<T>> applyExplosionDecay(item: ItemLike, functionBuilder: FunctionUserBuilder<T>): T = super.applyExplosionDecay<T>(item, functionBuilder)

	/** Generated override to expose protected method: [BlockLootSubProvider.applyExplosionCondition]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun <T : ConditionUserBuilder<T>> applyExplosionCondition(item: ItemLike, conditionBuilder: ConditionUserBuilder<T>): T = super.applyExplosionCondition<T>(item, conditionBuilder)

	/** Generated override to expose protected method: [BlockLootSubProvider.createSilkTouchDispatchTable]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createSilkTouchDispatchTable(block: Block, builder: LootPoolEntryContainer.Builder<*>): LootTable.Builder = super.createSilkTouchDispatchTable(block, builder)

	/** Generated override to expose protected method: [BlockLootSubProvider.createShearsDispatchTable]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createShearsDispatchTable(block: Block, builder: LootPoolEntryContainer.Builder<*>): LootTable.Builder = super.createShearsDispatchTable(block, builder)

	/** Generated override to expose protected method: [BlockLootSubProvider.createSilkTouchOrShearsDispatchTable]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createSilkTouchOrShearsDispatchTable(block: Block, builder: LootPoolEntryContainer.Builder<*>): LootTable.Builder = super.createSilkTouchOrShearsDispatchTable(block, builder)

	/** Generated override to expose protected method: [BlockLootSubProvider.createSingleItemTableWithSilkTouch]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createSingleItemTableWithSilkTouch(block: Block, item: ItemLike): LootTable.Builder = super.createSingleItemTableWithSilkTouch(block, item)

	/** Generated override to expose protected method: [BlockLootSubProvider.createSingleItemTable]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createSingleItemTable(item: ItemLike, count: NumberProvider): LootTable.Builder = super.createSingleItemTable(item, count)

	/** Generated override to expose protected method: [BlockLootSubProvider.createSingleItemTableWithSilkTouch]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createSingleItemTableWithSilkTouch(block: Block, item: ItemLike, count: NumberProvider): LootTable.Builder = super.createSingleItemTableWithSilkTouch(block, item, count)

	/** Generated override to expose protected method: [BlockLootSubProvider.createSilkTouchOnlyTable]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createSilkTouchOnlyTable(item: ItemLike): LootTable.Builder = super.createSilkTouchOnlyTable(item)

	/** Generated override to expose protected method: [BlockLootSubProvider.createPotFlowerItemTable]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createPotFlowerItemTable(item: ItemLike): LootTable.Builder = super.createPotFlowerItemTable(item)

	/** Generated override to expose protected method: [BlockLootSubProvider.createSlabItemTable]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createSlabItemTable(block: Block): LootTable.Builder = super.createSlabItemTable(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.createNameableBlockEntityTable]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createNameableBlockEntityTable(block: Block): LootTable.Builder = super.createNameableBlockEntityTable(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.createShulkerBoxDrop]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createShulkerBoxDrop(block: Block): LootTable.Builder = super.createShulkerBoxDrop(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.createCopperOreDrops]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createCopperOreDrops(block: Block): LootTable.Builder = super.createCopperOreDrops(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.createLapisOreDrops]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createLapisOreDrops(block: Block): LootTable.Builder = super.createLapisOreDrops(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.createRedstoneOreDrops]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createRedstoneOreDrops(block: Block): LootTable.Builder = super.createRedstoneOreDrops(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.createBannerDrop]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createBannerDrop(block: Block): LootTable.Builder = super.createBannerDrop(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.createBeeNestDrop]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createBeeNestDrop(block: Block): LootTable.Builder = super.createBeeNestDrop(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.createBeeHiveDrop]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createBeeHiveDrop(block: Block): LootTable.Builder = super.createBeeHiveDrop(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.createCaveVinesDrop]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createCaveVinesDrop(block: Block): LootTable.Builder = super.createCaveVinesDrop(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.createOreDrop]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createOreDrop(block: Block, item: Item): LootTable.Builder = super.createOreDrop(block, item)

	/** Generated override to expose protected method: [BlockLootSubProvider.createMushroomBlockDrop]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createMushroomBlockDrop(block: Block, item: ItemLike): LootTable.Builder = super.createMushroomBlockDrop(block, item)

	/** Generated override to expose protected method: [BlockLootSubProvider.createGrassDrops]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createGrassDrops(block: Block): LootTable.Builder = super.createGrassDrops(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.createMultifaceBlockDrops]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createMultifaceBlockDrops(block: Block, builder: LootItemCondition.Builder): LootTable.Builder = super.createMultifaceBlockDrops(block, builder)

	/** Generated override to expose protected method: [BlockLootSubProvider.createLeavesDrops]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createLeavesDrops(leavesBlock: Block, saplingBlock: Block, vararg chances: Float): LootTable.Builder = super.createLeavesDrops(leavesBlock, saplingBlock, *chances)

	/** Generated override to expose protected method: [BlockLootSubProvider.createOakLeavesDrops]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createOakLeavesDrops(oakLeavesBlock: Block, saplingBlock: Block, vararg chances: Float): LootTable.Builder = super.createOakLeavesDrops(oakLeavesBlock, saplingBlock, *chances)

	/** Generated override to expose protected method: [BlockLootSubProvider.createMangroveLeavesDrops]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createMangroveLeavesDrops(block: Block): LootTable.Builder = super.createMangroveLeavesDrops(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.createCropDrops]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createCropDrops(cropBlock: Block, grownCropItem: Item, seedsItem: Item, dropGrownCropCondition: LootItemCondition.Builder): LootTable.Builder =
		super.createCropDrops(cropBlock, grownCropItem, seedsItem, dropGrownCropCondition)

	/** Generated override to expose protected method: [BlockLootSubProvider.createDoublePlantShearsDrop]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createDoublePlantShearsDrop(sheared: Block): LootTable.Builder = super.createDoublePlantShearsDrop(sheared)

	/** Generated override to expose protected method: [BlockLootSubProvider.createDoublePlantWithSeedDrops]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createDoublePlantWithSeedDrops(block: Block, sheared: Block): LootTable.Builder = super.createDoublePlantWithSeedDrops(block, sheared)

	/** Generated override to expose protected method: [BlockLootSubProvider.createCandleDrops]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createCandleDrops(candleBlock: Block): LootTable.Builder = super.createCandleDrops(candleBlock)

	public override fun createCopperGolemStatueBlock(block: Block): LootTable.Builder = super.createCopperGolemStatueBlock(block)

	public override fun createShearsOnlyDrop(drop: ItemLike): LootTable.Builder = super.createShearsOnlyDrop(drop)

	/** Generated override to expose protected method: [BlockLootSubProvider.addNetherVinesDropTable]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun addNetherVinesDropTable(vines: Block, plant: Block) = super.addNetherVinesDropTable(vines, plant)

	/** Generated override to expose protected method: [BlockLootSubProvider.createDoorTable]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun createDoorTable(doorBlock: Block): LootTable.Builder = super.createDoorTable(doorBlock)

	/** Generated override to expose protected method: [BlockLootSubProvider.dropPottedContents]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun dropPottedContents(flowerPot: Block) = super.dropPottedContents(flowerPot)

	/** Generated override to expose protected method: [BlockLootSubProvider.otherWhenSilkTouch]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun otherWhenSilkTouch(block: Block, other: Block) = super.otherWhenSilkTouch(block, other)

	/** Generated override to expose protected method: [BlockLootSubProvider.dropOther]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun dropOther(block: Block, item: ItemLike) = super.dropOther(block, item)

	/** Generated override to expose protected method: [BlockLootSubProvider.dropWhenSilkTouch]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun dropWhenSilkTouch(block: Block) = super.dropWhenSilkTouch(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.dropSelf]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun dropSelf(block: Block) = super.dropSelf(block)

	/** Generated override to expose protected method: [BlockLootSubProvider.add]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
	public override fun add(block: Block, builder: LootTable.Builder) = super.add(block, builder)
	// GENERATED OVERRIDE END

	companion object {
		// GENERATED STATIC START

		/** Generated override to expose protected method: [BlockLootSubProvider.createCandleCakeDrops]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateBlockLootTables"], date = "Tue, 28 Apr 2026 01:43:43 GMT")
		@JvmStatic
		fun createCandleCakeDrops(candleCakeBlock: Block): LootTable.Builder = BlockLootSubProvider.createCandleCakeDrops(candleCakeBlock)
		// GENERATED STATIC END
	}
}
