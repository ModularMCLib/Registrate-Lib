package com.modularmc.registrate.providers.loot

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.core.HolderGetter
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.data.loot.EntityLootSubProvider
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition

import java.util.function.Consumer
import java.util.stream.Stream
import javax.annotation.processing.Generated

open class RegistrateEntityLootTables(provider: HolderLookup.Provider, private val parent: AbstractRegistrate<*>, private val callback: Consumer<RegistrateEntityLootTables>) :
	EntityLootSubProvider(FeatureFlags.REGISTRY.allFlags(), provider),
	RegistrateLootTables {
	private val itemLookup = registries.lookupOrThrow(Registries.ITEM)
	private val blockLookup = registries.lookupOrThrow(Registries.BLOCK)
	private val entityLookup = registries.lookupOrThrow(Registries.ENTITY_TYPE)

	override fun generate() {
		callback.accept(this)
	}

	override fun getKnownEntityTypes(): Stream<EntityType<*>> = parent.getAll<EntityType<*>, EntityType<*>>(Registries.ENTITY_TYPE).stream()
		.map { it.get() }

	fun getRegistries(): HolderLookup.Provider = registries

	fun itemLookup(): HolderLookup<Item> = itemLookup

	fun blockLookup(): HolderLookup<Block> = blockLookup

	fun entityLookup(): HolderLookup<EntityType<*>> = entityLookup

	// GENERATED OVERRIDE START

	public override fun killedByFrog(entityTypes: HolderGetter<EntityType<*>>): LootItemCondition.Builder = super.killedByFrog(entityTypes)

	/** Generated override to expose protected method: [EntityLootSubProvider.add]  */
	@Generated(
		value = ["com.modularmc.registrate.test.meta.UpdateEntityLootTables"],
		date = "Tue, 28 Apr 2026 01:45:14 GMT",
	)
	public override fun add(entityType: EntityType<*>, builder: LootTable.Builder) {
		super.add(entityType, builder)
	}

	/** Generated override to expose protected method: [EntityLootSubProvider.add]  */
	@Generated(
		value = ["com.modularmc.registrate.test.meta.UpdateEntityLootTables"],
		date = "Tue, 28 Apr 2026 01:45:14 GMT",
	)
	public override fun add(entityType: EntityType<*>, defaultLootTable: ResourceKey<LootTable>, builder: LootTable.Builder) {
		super.add(entityType, defaultLootTable, builder)
	}
	// GENERATED OVERRIDE END
}
