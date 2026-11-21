package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.neoforged.neoforge.registries.DeferredHolder

open class EntityEntry<T : Entity>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<EntityType<*>, EntityType<T>>) : RegistryEntry<EntityType<*>, EntityType<T>>(owner, delegate) {
	open fun create(world: Level): T? = get().create(world)

	open fun `is`(entity: Entity?): Boolean = entity != null && entity.type === get()

	companion object {
		@JvmStatic
		@Suppress("UNCHECKED_CAST")
		fun <T : Entity> cast(entry: RegistryEntry<EntityType<*>, EntityType<T>>): EntityEntry<T> = entry as EntityEntry<T>
	}
}
