package com.modularmc.registrate.providers.loot

import net.minecraft.core.WritableRegistry
import net.minecraft.data.loot.LootTableSubProvider
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.ValidationContextSource

/**
 * 生成某一类掉落表。实现可在本轮数据生成收集完全部掉落表后执行校验。
 */
interface RegistrateLootTables : LootTableSubProvider {
	/**
	 * 校验本轮生成的掉落表，并将发现的问题写入指定上下文。
	 *
	 * @param tables 本轮收集到的掉落表注册表
	 * @param validationContext 用于记录问题的校验上下文
	 */
	fun validate(tables: WritableRegistry<LootTable>, validationContext: ValidationContextSource) {}
}
