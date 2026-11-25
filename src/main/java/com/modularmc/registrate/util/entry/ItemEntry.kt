package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredHolder

/** 物品的延迟注册引用，绑定后可直接用于创建物品堆叠。 */
open class ItemEntry<T : Item>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<Item, T>) : ItemProviderEntry<Item, T>(owner, delegate) {
	companion object {
		/** 将通用引用转换为物品专用引用。传入的实际包装类型须为 [ItemEntry]。 */
		@JvmStatic
		@Suppress("UNCHECKED_CAST")
		fun <T : Item> cast(entry: RegistryEntry<Item, T>): ItemEntry<T> = entry as ItemEntry<T>
	}
}
