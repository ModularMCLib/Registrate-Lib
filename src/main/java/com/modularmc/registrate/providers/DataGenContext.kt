package com.modularmc.registrate.providers

import com.modularmc.registrate.builders.base.Builder
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.Registry
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey

/** 数据生成回调使用的延迟注册条目，附带注册时分配的名称和标识。 */
data class DataGenContext<R : Any, E : R>(private val supplier: NonNullSupplier<E>, val name: String, val id: Identifier) : NonNullSupplier<E> by supplier {
	val entry: E
		get() = supplier.get()

	companion object {
		/** 从已提交的构建器取得条目信息。旧重载保留了 [type]，实际查询使用构建器自身的注册表键。 */
		@JvmStatic
		@Deprecated("The builder already identifies its registry", ReplaceWith("from(builder)"))
		@Suppress("UNUSED_PARAMETER")
		fun <R : Any, E : R> from(builder: Builder<R, E, *, *>, type: ResourceKey<out Registry<R>>): DataGenContext<R, E> = from(builder)

		/**
		 * 从所属 Registrate 取得条目引用，并记录名称和完整标识。调用时无需读取已绑定的对象。
		 *
		 * @param builder 已提交注册的条目构建器
		 * @return 供生成回调使用的上下文
		 * @throws IllegalArgumentException 构建器对应的条目尚未提交
		 */
		@JvmStatic
		fun <R : Any, E : R> from(builder: Builder<R, E, *, *>): DataGenContext<R, E> {
			val owner = builder.owner
			val name = builder.name
			val entry = owner.get<R, E>(name, builder.registryKey)
			return DataGenContext(entry, name, entry.id)
		}
	}
}
