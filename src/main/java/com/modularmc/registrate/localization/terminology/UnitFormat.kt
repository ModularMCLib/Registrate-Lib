package com.modularmc.registrate.localization.terminology

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 按 `DecimalFormat` 格式化数值，并在数值后拼接已登记的单位符号。 */
@JvmRecord
data class UnitFormat(val numberPattern: String, val decimalSeparator: Char, val spacing: String) {
	/** 使用当前小数分隔符和数字格式生成数值，再拼接设置的间距及 [symbol]。 */
	fun format(value: Number, symbol: String): String {
		val symbols = DecimalFormatSymbols.getInstance(Locale.ROOT).apply { decimalSeparator = this@UnitFormat.decimalSeparator }
		return DecimalFormat(numberPattern, symbols).format(value) + spacing + symbol
	}
}
