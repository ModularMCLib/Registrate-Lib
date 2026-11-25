package com.modularmc.registrate.util.nullness

import java.util.function.Consumer

/** 在调用线程上同步接收非空值。组合后的回调按组合顺序执行。 */
fun interface NonNullConsumer<T : Any> : Consumer<T> {
	/** 在调用线程上处理该非空值，回调抛出的异常直接传给调用方。 */
	override fun accept(value: T)

	/** 返回组合后的回调，依次对同一输入运行当前回调和 [after]。前一个回调抛出异常时，后一个回调不会运行。 */
	fun andThen(after: NonNullConsumer<in T>): NonNullConsumer<T> = NonNullConsumer { value ->
		accept(value)
		after.accept(value)
	}

	companion object {
		/** 返回可用作默认回调的空操作，接收值后不产生副作用。 */
		@JvmStatic
		fun <T : Any> noop(): NonNullConsumer<T> = NonNullConsumer {}
	}
}
