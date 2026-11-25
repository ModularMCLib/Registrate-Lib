package com.modularmc.registrate.util.entry

import com.modularmc.registrate.util.nullness.NonNullSupplier

/** 首次取值时解析并保存注册引用。缓存在调用线程初始化，调用方负责单线程访问或同步。 */
open class LazyRegistryEntry<R : Any, T : R>(supplier: NonNullSupplier<out RegistryEntry<R, T>>) : NonNullSupplier<T> {
	private val holder by lazy(LazyThreadSafetyMode.NONE) { supplier.get() }

	/** 获取已绑定的条目对象。首次调用时还会通过取值器查找注册引用。 */
	override fun get(): T = holder.get()
}
