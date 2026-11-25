package com.modularmc.registrate.util

import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters
import net.minecraft.world.item.CreativeModeTab.TabVisibility
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike

import org.jetbrains.annotations.ApiStatus

import java.util.function.BiConsumer
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/** 封装创造模式标签页填充事件，供回调读取启用条件并给当前标签页添加物品。 */
class CreativeModeTabModifier @ApiStatus.Internal constructor(
	private val flags: Supplier<FeatureFlagSet>,
	private val hasPermissions: BooleanSupplier,
	private val acceptFunc: BiConsumer<ItemStack, TabVisibility>,
	private val parameters: Supplier<ItemDisplayParameters>,
) : CreativeModeTab.Output {
	/** 返回当前物品展示上下文的功能标记。 */
	fun getFlags(): FeatureFlagSet = flags.get()

	/** 返回当前标签页的完整物品展示参数。 */
	fun getParameters(): ItemDisplayParameters = parameters.get()

	/** 返回当前展示上下文是否具有显示受权限限制内容的权限。 */
	fun hasPermissions(): Boolean = hasPermissions.asBoolean

	/** 将给定物品堆叠按 [visibility] 设置加入标签页或搜索页。 */
	override fun accept(stack: ItemStack, visibility: TabVisibility) {
		acceptFunc.accept(stack, visibility)
	}

	/** 取得已绑定的物品或方块，按指定可见性加入当前标签页。 */
	fun accept(item: Supplier<out ItemLike>, visibility: TabVisibility) {
		accept(item.get(), visibility)
	}

	/** 取得已绑定的物品或方块，按原版默认可见性加入当前标签页。 */
	fun accept(item: Supplier<out ItemLike>) {
		accept(item.get())
	}
}
