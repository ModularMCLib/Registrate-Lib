package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.registries.DeferredHolder

/** 方块的延迟注册引用，绑定后可读取默认状态或作为关联方块物品使用。 */
open class BlockEntry<T : Block>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<Block, T>) : ItemProviderEntry<Block, T>(owner, delegate) {
	/** 取得已注册方块的默认状态。 */
	open fun getDefaultState(): BlockState = get().defaultBlockState()

	/** 检查给定状态是否属于这个已经注册的方块。 */
	open fun has(state: BlockState): Boolean = `is`(state.block)

	companion object {
		/** 将通用引用转换为方块专用引用。传入的实际包装类型须为 [BlockEntry]。 */
		@JvmStatic
		@Suppress("UNCHECKED_CAST")
		fun <T : Block> cast(entry: RegistryEntry<Block, T>): BlockEntry<T> = entry as BlockEntry<T>
	}
}
