package com.modularmc.registrate.providers.loot

import net.minecraft.core.WritableRegistry
import net.minecraft.data.loot.LootTableSubProvider
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.ValidationContext

/**
 * 生成某一类掉落表。实现可在本轮数据生成收集完全部掉落表后执行校验。
 */
interface RegistrateLootTables : LootTableSubProvider {
	/** Validates generated tables and reports problems into the supplied context.  */
	fun validate(tables: WritableRegistry<LootTable>, validationContext: ValidationContext) {}
}
