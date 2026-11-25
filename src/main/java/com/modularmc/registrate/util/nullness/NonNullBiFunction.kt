package com.modularmc.registrate.util.nullness

import java.util.function.BiFunction

/** 在调用线程上同步将两个非空输入转换为非空结果。 */
fun interface NonNullBiFunction<T : Any, U : Any, R : Any> : BiFunction<T, U, R> {
	/** 同步转换这两个非空输入，返回非空结果。转换抛出的异常直接传给调用方。 */
	override fun apply(first: T, second: U): R
}
