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

/** 生成所属模组的实体掉落表，使用当前数据生成轮次的注册表查询。 */
open class RegistrateEntityLootTables(provider: HolderLookup.Provider, private val parent: AbstractRegistrate<*>, private val callback: Consumer<RegistrateEntityLootTables>) :
	EntityLootSubProvider(FeatureFlags.REGISTRY.allFlags(), provider),
	RegistrateLootTables {
	private val itemLookup = registries.lookupOrThrow(Registries.ITEM)
	private val blockLookup = registries.lookupOrThrow(Registries.BLOCK)
	private val entityLookup = registries.lookupOrThrow(Registries.ENTITY_TYPE)

	/** 执行本轮实体掉落表的生成回调。 */
	override fun generate() {
		callback.accept(this)
	}

	/** 返回所属模组已注册的实体类型，供原版掉落表收集流程遍历。 */
	override fun getKnownEntityTypes(): Stream<EntityType<*>> = parent.getAll<EntityType<*>, EntityType<*>>(Registries.ENTITY_TYPE).stream()
		.map { it.get() }

	/** 返回实体掉落表生成时使用的注册表查询集合。 */
	fun getRegistries(): HolderLookup.Provider = registries

	/** 返回实体掉落表使用的物品注册表查询。 */
	fun itemLookup(): HolderLookup<Item> = itemLookup

	/** 返回实体掉落表使用的方块注册表查询。 */
	fun blockLookup(): HolderLookup<Block> = blockLookup

	/** 返回实体掉落表使用的实体类型注册表查询。 */
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
