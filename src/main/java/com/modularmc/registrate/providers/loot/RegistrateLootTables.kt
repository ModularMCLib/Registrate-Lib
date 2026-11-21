package com.modularmc.registrate.providers.loot

import net.minecraft.core.WritableRegistry
import net.minecraft.data.loot.LootTableSubProvider
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.ValidationContextSource

/**
 * A loot subprovider with an optional validation step after all tables are collected.
 * Implementations generate and validate tables during the same datagen run.
 */
interface RegistrateLootTables : LootTableSubProvider {
	/** Validates generated tables and reports problems into the supplied context.  */
	fun validate(tables: WritableRegistry<LootTable>, validationContext: ValidationContextSource) {}
}
