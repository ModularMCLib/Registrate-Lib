package com.modularmc.registrate.util.nullness

/** 供构建器的属性配置等回调使用，将非空值转换为同一类型的非空值。 */
fun interface NonNullUnaryOperator<T : Any> : NonNullFunction<T, T> {
	/** 返回按调用顺序组合的转换。任一步抛出的异常都会传给调用方。 */
	fun <V> andThen(after: NonNullUnaryOperator<T>): NonNullUnaryOperator<T> = NonNullUnaryOperator { after.apply(apply(it)) }

	companion object {
		/** 返回可用于构建器默认属性回调的恒等转换。 */
		@JvmStatic
		fun <T : Any> identity(): NonNullUnaryOperator<T> = NonNullUnaryOperator { it }
	}
}
