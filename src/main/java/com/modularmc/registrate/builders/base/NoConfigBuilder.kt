package com.modularmc.registrate.builders.base

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey

/** 供没有专用构建器的条目使用。在通用注册、标签和数据生成配置之外，由工厂负责创建对象。 */
open class NoConfigBuilder<R : Any, T : R, P : Any>(
	owner: AbstractRegistrate<*>,
	parent: P,
	name: String,
	callback: BuilderCallback,
	registryType: ResourceKey<out Registry<R>>,
	private val factory: NonNullSupplier<T>,
) : AbstractBuilder<R, T, P, NoConfigBuilder<R, T, P>>(owner, parent, name, callback, registryType) {
	/** 在注册时调用传入的条目工厂。 */
	override fun createEntry(): T = factory.get()
}
