package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike
import net.neoforged.neoforge.registries.DeferredHolder

open class ItemProviderEntry<R : ItemLike, T : R>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<R, T>) :
	RegistryEntry<R, T>(owner, delegate),
	ItemLike {
	open fun asStack(): ItemStack = ItemStack(this)

	open fun asStack(count: Int): ItemStack = ItemStack(this, count)

	open fun isIn(stack: ItemStack): Boolean = `is`(stack.item)

	open fun `is`(item: Item): Boolean = asItem() === item

	override fun asItem(): Item = get().asItem()
}
