package com.modularmc.registrate.util.nullness

import net.neoforged.neoforge.common.util.Lazy

import java.util.Objects
import java.util.function.Supplier

/** 供注册和数据生成回调按需取得非空值。 */
fun interface NonNullSupplier<T : Any> : Supplier<T> {
	/** 在调用线程上取得非空值，取值时抛出的异常直接传给调用方。 */
	override fun get(): T

	/** 返回仅在首次取值时计算并缓存结果的取值器。 */
	fun lazy(): NonNullSupplier<T> = lazy(this)

	companion object {
		/** 将 Java 的取值器适配为非空取值器。调用适配后的取值器时，若原取值器返回 `null`，则抛出 `NullPointerException`。 */
		@JvmStatic
		fun <T : Any> of(supplier: Supplier<T>): NonNullSupplier<T> = of(supplier) { "Unexpected null value from supplier" }

		/** 包装已有的 Java 取值器。源取值器返回 `null` 时抛出 [NullPointerException]，并延迟生成 [errorMessage]。 */
		@JvmStatic
		fun <T : Any> of(supplier: Supplier<T>, errorMessage: NonNullSupplier<String>): NonNullSupplier<T> = NonNullSupplier { Objects.requireNonNull(supplier.get(), errorMessage) }

		/** 通过 NeoForge 的 [Lazy] 在首次取值时计算，并缓存所得结果。 */
		@JvmStatic
		fun <T : Any> lazy(supplier: Supplier<T>): NonNullSupplier<T> {
			val value = Lazy.of(supplier)
			return NonNullSupplier { value.get() }
		}
	}
}
