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

class CreativeModeTabModifier @ApiStatus.Internal constructor(
	private val flags: Supplier<FeatureFlagSet>,
	private val hasPermissions: BooleanSupplier,
	private val acceptFunc: BiConsumer<ItemStack, TabVisibility>,
	private val parameters: Supplier<ItemDisplayParameters>,
) : CreativeModeTab.Output {
	fun getFlags(): FeatureFlagSet = flags.get()

	fun getParameters(): ItemDisplayParameters = parameters.get()

	fun hasPermissions(): Boolean = hasPermissions.asBoolean

	override fun accept(stack: ItemStack, visibility: TabVisibility) {
		acceptFunc.accept(stack, visibility)
	}

	fun accept(item: Supplier<out ItemLike>, visibility: TabVisibility) {
		accept(item.get(), visibility)
	}

	fun accept(item: Supplier<out ItemLike>) {
		accept(item.get())
	}
}
