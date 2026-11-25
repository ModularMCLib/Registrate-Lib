package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.neoforged.neoforge.registries.DeferredHolder

import java.util.Optional
import java.util.function.Predicate

/** 延迟绑定的注册条目引用，可读取注册元数据，也可在其他注册表中查询同名条目。 */
open class RegistryEntry<R : Any, S : R>(private val owner: AbstractRegistrate<*>, holder: DeferredHolder<R, S>) :
	DeferredHolder<R, S>(holder.key),
	NonNullSupplier<S> {
	/** 返回该条目附带的声明。注册引用尚未绑定时也可以读取。 */
	open fun metadata(): EntryMetadata = owner.getEntryMetadata(key)

	/** 按注册表键查询当前模组提交的同名条目，缺少时抛出 [IllegalArgumentException]。 */
	open fun <X : Any, Y : X> getSibling(registryType: ResourceKey<out Registry<X>>): RegistryEntry<X, Y> = owner.get(id.path, registryType)

	/** 按注册表键查询当前模组提交的同名条目，缺少时返回空值。 */
	open fun <X : Any, Y : X> getSiblingOptional(registryType: ResourceKey<out Registry<X>>): Optional<RegistryEntry<X, Y>> = owner.getOptional(id.path, registryType)

	/** 在指定注册表中查询当前模组提交的同名条目，缺少时抛出 [IllegalArgumentException]。 */
	open fun <X : Any, Y : X> getSibling(registry: Registry<X>): RegistryEntry<X, Y> = getSibling(registry.key())

	/** 在指定注册表中查询当前模组提交的同名条目，缺少时返回空值。 */
	open fun <X : Any, Y : X> getSiblingOptional(registry: Registry<X>): Optional<RegistryEntry<X, Y>> = getSiblingOptional(registry.key())

	/** 对已绑定对象执行 [predicate]。匹配时返回本引用，否则返回空值。 */
	open fun filter(predicate: Predicate<R>): Optional<RegistryEntry<R, S>> = if (predicate.test(get())) Optional.of(this) else Optional.empty()

	/** 按对象实例比较给定值与当前已绑定的条目。 */
	open fun <X> `is`(entry: X): Boolean = get() === entry

	companion object {
		@JvmStatic
		@Suppress("UNCHECKED_CAST")
		protected fun <E : RegistryEntry<*, *>> cast(type: Class<in E>, entry: RegistryEntry<*, *>): E = type.cast(entry) as E
	}
}
