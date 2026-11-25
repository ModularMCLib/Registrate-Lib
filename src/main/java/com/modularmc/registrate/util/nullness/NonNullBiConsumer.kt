package com.modularmc.registrate.util.nullness

import java.util.function.BiConsumer

/** 供注册和数据生成回调使用，在调用线程上同步接收两个非空值。 */
fun interface NonNullBiConsumer<T : Any, U : Any> : BiConsumer<T, U> {
	/** 在调用线程上处理两个非空值，回调抛出的异常直接传给调用方。 */
	override fun accept(first: T, second: U)

	companion object {
		/** 返回可用作默认回调的空操作，对传入的两个值无副作用。 */
		@JvmStatic
		fun <T : Any, U : Any> noop(): NonNullBiConsumer<T, U> = NonNullBiConsumer { _, _ -> }
	}
}
