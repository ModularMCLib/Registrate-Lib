package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.neoforged.neoforge.registries.DeferredHolder

/** 实体类型的延迟引用，在注册绑定后可创建并识别相应实体实例。 */
open class EntityEntry<T : Entity>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<EntityType<*>, EntityType<T>>) : RegistryEntry<EntityType<*>, EntityType<T>>(owner, delegate) {
	/** 在指定世界中构造一个实体实例，调用方负责将它加入世界。创建失败时可返回 `null`。 */
	open fun create(world: Level, reason: EntitySpawnReason): T? = get().create(world, reason)

	/** 判断实体是否属于此注册类型。传入 `null` 时返回假。 */
	open fun `is`(entity: Entity?): Boolean = entity != null && entity.type === get()

	companion object {
		/** 将通用引用转换为实体专用引用。传入的实际包装类型须为 [EntityEntry]。 */
		@JvmStatic
		@Suppress("UNCHECKED_CAST")
		fun <T : Entity> cast(entry: RegistryEntry<EntityType<*>, EntityType<T>>): EntityEntry<T> = entry as EntityEntry<T>
	}
}
