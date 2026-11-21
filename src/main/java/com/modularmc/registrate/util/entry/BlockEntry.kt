package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.registries.DeferredHolder

open class BlockEntry<T : Block>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<Block, T>) : ItemProviderEntry<Block, T>(owner, delegate) {
	open fun getDefaultState(): BlockState = get().defaultBlockState()

	open fun has(state: BlockState): Boolean = `is`(state.block)

	companion object {
		@JvmStatic
		@Suppress("UNCHECKED_CAST")
		fun <T : Block> cast(entry: RegistryEntry<Block, T>): BlockEntry<T> = entry as BlockEntry<T>
	}
}
