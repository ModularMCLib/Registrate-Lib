package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.fluids.BaseFlowingFluid
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.registries.DeferredHolder

import java.util.Optional

open class FluidEntry<T : BaseFlowingFluid>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<Fluid, T>) : RegistryEntry<Fluid, T>(owner, delegate) {
	private val block = getSiblingOptional<Block, Block>(BuiltInRegistries.BLOCK).map { BlockEntry.cast(it) }.orElse(null)

	override fun <R> `is`(entry: R): Boolean = get().isSame(entry as Fluid)

	@Suppress("UNCHECKED_CAST")
	open fun <S : BaseFlowingFluid> getSource(): S = get().source as S

	open fun getType(): FluidType = get().fluidType

	@Suppress("UNCHECKED_CAST")
	open fun <B : Block> getBlock(): Optional<B> = Optional.ofNullable(block?.get() as B?)

	@Suppress("UNCHECKED_CAST")
	open fun <I : Item> getBucket(): Optional<I> = Optional.ofNullable(get().bucket.takeUnless { it === Items.AIR } as I?)
}
