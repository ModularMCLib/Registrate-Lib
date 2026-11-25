package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.registries.DeferredHolder

import java.util.Optional

/** 方块实体类型的延迟引用，可在注册绑定后创建实例或按位置查找匹配的方块实体。 */
open class BlockEntityEntry<T : BlockEntity>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<BlockEntityType<*>, BlockEntityType<T>>) :
	RegistryEntry<BlockEntityType<*>, BlockEntityType<T>>(owner, delegate) {
	/** 使用已注册类型创建当前位置对应的方块实体。类型工厂返回空值时返回 `null`。 */
	open fun create(pos: BlockPos, state: BlockState): T? = get().create(pos, state)

	/** 判断指定方块实体是否由此类型创建。传入 `null` 时返回假。 */
	open fun `is`(entity: BlockEntity?): Boolean = entity != null && entity.type === get()

	/** 查询指定位置的方块实体。为空或类型不匹配时返回空值。 */
	open fun get(world: BlockGetter, pos: BlockPos): Optional<T> = Optional.ofNullable(getNullable(world, pos))

	/** 查询指定位置的方块实体。为空或类型不匹配时返回 `null`。 */
	@Suppress("UNCHECKED_CAST")
	open fun getNullable(world: BlockGetter, pos: BlockPos): T? {
		val entity = world.getBlockEntity(pos)
		return if (`is`(entity)) entity as T else null
	}

	companion object {
		/** 将通用引用转换为方块实体专用引用。传入的实际包装类型须为 [BlockEntityEntry]。 */
		@JvmStatic
		@Suppress("UNCHECKED_CAST")
		fun <T : BlockEntity> cast(entry: RegistryEntry<BlockEntityType<*>, BlockEntityType<T>>): BlockEntityEntry<T> = entry as BlockEntityEntry<T>
	}
}
