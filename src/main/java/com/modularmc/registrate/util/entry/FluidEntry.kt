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

/** 流动流体的延迟引用。绑定后可以取得源流体、共享流体类型和可能存在的附属条目。 */
open class FluidEntry<T : BaseFlowingFluid>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<Fluid, T>) : RegistryEntry<Fluid, T>(owner, delegate) {
	private val block = getSiblingOptional<Block, Block>(BuiltInRegistries.BLOCK).map { BlockEntry.cast(it) }.orElse(null)

	/** 按流体自身的等价规则判断是否同一种流体，源流体和流动流体可以匹配。参数须为流体。 */
	override fun <R> `is`(entry: R): Boolean = get().isSame(entry as Fluid)

	/** 取得与当前流动流体配对的源流体。调用方须选择源流体的实际类型。 */
	@Suppress("UNCHECKED_CAST")
	open fun <S : BaseFlowingFluid> getSource(): S = get().source as S

	/** 返回源流体与流动流体共用的流体类型。 */
	open fun getType(): FluidType = get().fluidType

	/** 返回注册时关联的流体方块。未登记方块时返回空值。 */
	@Suppress("UNCHECKED_CAST")
	open fun <B : Block> getBlock(): Optional<B> = Optional.ofNullable(block?.get() as B?)

	/** 返回流体定义的桶物品。流体把空气作为桶占位时返回空值。 */
	@Suppress("UNCHECKED_CAST")
	open fun <I : Item> getBucket(): Optional<I> = Optional.ofNullable(get().bucket.takeUnless { it === Items.AIR } as I?)
}
