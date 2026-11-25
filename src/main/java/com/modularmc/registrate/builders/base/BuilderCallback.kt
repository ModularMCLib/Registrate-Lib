package com.modularmc.registrate.builders.base

import com.modularmc.registrate.util.entry.RegistryEntry
import com.modularmc.registrate.util.nullness.NonNullFunction
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.neoforged.neoforge.registries.DeferredHolder

/**
 * 接收构建完成的条目，将对象创建推迟到 NeoForge 注册阶段。
 * 所属 Registrate 在模组初始化时将此回调交给构建器。构建器配置完成后调用一次。
 */
interface BuilderCallback {
	/** 将条目、对象工厂和引用工厂提交到待注册队列，返回该条目的延迟引用。 */
	fun <R : Any, T : R> accept(
		name: String,
		type: ResourceKey<out Registry<R>>,
		builder: Builder<R, T, *, *>,
		factory: NonNullSupplier<out T>,
		entryFactory: NonNullFunction<DeferredHolder<R, T>, out RegistryEntry<R, T>>,
	): RegistryEntry<R, T>

	/** 用目标注册表的默认引用类型提交条目。 */
	fun <R : Any, T : R> accept(name: String, type: ResourceKey<out Registry<R>>, builder: Builder<R, T, *, *>, factory: NonNullSupplier<out T>): RegistryEntry<R, T> =
		accept(name, type, builder, factory) { RegistryEntry(builder.owner, it) }
}
