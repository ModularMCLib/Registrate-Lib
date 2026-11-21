package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredHolder

open class ItemEntry<T : Item>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<Item, T>) : ItemProviderEntry<Item, T>(owner, delegate) {
	companion object {
		@JvmStatic
		@Suppress("UNCHECKED_CAST")
		fun <T : Item> cast(entry: RegistryEntry<Item, T>): ItemEntry<T> = entry as ItemEntry<T>
	}
}
