package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike
import net.neoforged.neoforge.registries.DeferredHolder

/** 供能映射为物品的注册条目使用，让物品和方块引用共用堆叠构造入口。 */
open class ItemProviderEntry<R : ItemLike, T : R>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<R, T>) :
	RegistryEntry<R, T>(owner, delegate),
	ItemLike {
	/** 为关联物品创建一个数量为一的新堆叠。 */
	open fun asStack(): ItemStack = ItemStack(this)

	/** 为关联物品创建一个数量为 [count] 的新堆叠。 */
	open fun asStack(count: Int): ItemStack = ItemStack(this, count)

	open fun isIn(stack: ItemStack): Boolean = `is`(stack.item)

	/** 按物品实例判断是否为此条目对应的物品。 */
	open fun `is`(item: Item): Boolean = asItem() === item

	/** 将已经绑定的条目转换为对应的物品。 */
	override fun asItem(): Item = get().asItem()
}
