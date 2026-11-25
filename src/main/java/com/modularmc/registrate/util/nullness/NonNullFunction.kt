package com.modularmc.registrate.util.nullness

import java.util.function.Function

/** 将非空输入转换为非空结果，组合后的每一步也使用非空值。 */
fun interface NonNullFunction<T : Any, R : Any> : Function<T, R> {
	/** 同步转换该非空值，返回非空结果。转换抛出的异常直接传给调用方。 */
	override fun apply(value: T): R

	/** 返回非空值转换的组合，将当前转换结果作为 [after] 的输入。 */
	fun <V : Any> andThen(after: NonNullFunction<in R, out V>): NonNullFunction<T, V> = NonNullFunction { after.apply(apply(it)) }
}
