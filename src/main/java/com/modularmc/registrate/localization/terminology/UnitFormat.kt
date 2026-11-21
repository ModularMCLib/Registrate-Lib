package com.modularmc.registrate.localization.terminology

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formats a number followed by the registered unit symbol. The pattern uses DecimalFormat syntax. */
@JvmRecord
data class UnitFormat(val numberPattern: String, val decimalSeparator: Char, val spacing: String) {
	fun format(value: Number, symbol: String): String {
		val symbols = DecimalFormatSymbols.getInstance(Locale.ROOT).apply { decimalSeparator = this@UnitFormat.decimalSeparator }
		return DecimalFormat(numberPattern, symbols).format(value) + spacing + symbol
	}
}
