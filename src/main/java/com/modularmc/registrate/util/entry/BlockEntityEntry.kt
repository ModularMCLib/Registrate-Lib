package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.registries.DeferredHolder

import java.util.Optional

open class BlockEntityEntry<T : BlockEntity>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<BlockEntityType<*>, BlockEntityType<T>>) :
	RegistryEntry<BlockEntityType<*>, BlockEntityType<T>>(owner, delegate) {
	open fun create(pos: BlockPos, state: BlockState): T? = get().create(pos, state)

	open fun `is`(entity: BlockEntity?): Boolean = entity != null && entity.type === get()

	open fun get(world: BlockGetter, pos: BlockPos): Optional<T> = Optional.ofNullable(getNullable(world, pos))

	@Suppress("UNCHECKED_CAST")
	open fun getNullable(world: BlockGetter, pos: BlockPos): T? {
		val entity = world.getBlockEntity(pos)
		return if (`is`(entity)) entity as T else null
	}

	companion object {
		@JvmStatic
		@Suppress("UNCHECKED_CAST")
		fun <T : BlockEntity> cast(entry: RegistryEntry<BlockEntityType<*>, BlockEntityType<T>>): BlockEntityEntry<T> = entry as BlockEntityEntry<T>
	}
}
