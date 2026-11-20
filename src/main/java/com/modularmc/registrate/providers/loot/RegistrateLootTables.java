package com.modularmc.registrate.providers.loot;

import net.minecraft.core.WritableRegistry;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;

/**
 * A loot subprovider with an optional validation step after all tables are collected.
 * Implementations generate and validate tables during the same datagen run.
 */
public interface RegistrateLootTables extends LootTableSubProvider {

    /** Validates generated tables and reports problems into the supplied context. */
    default void validate(WritableRegistry<LootTable> tables, ValidationContext validationContext) {}
}
