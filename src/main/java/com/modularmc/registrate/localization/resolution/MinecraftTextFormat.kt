package com.modularmc.registrate.localization.resolution

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntMaps
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap

import java.util.regex.Pattern

internal object MinecraftTextFormat {
	private const val ARGUMENT = "%(?:(\\d+)\\$)?s|%%"
	private const val PROTECTED = "$ARGUMENT|(§[^\\r\\n]|\\R)|https?://[^\\s<>§]+"
	private val argumentPattern = Pattern.compile(ARGUMENT)
	private val protectedPattern = Pattern.compile(PROTECTED)
	private const val NORMAL = "abcdefghijklmn\u00F1opqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789()[]{}<>\u25C1\u25B7_,;.?!/\\'"
	private const val INVERTED =
		"\u0250q\u0254p\u01DD\u025Fb\u0265\u0131\u0638\u029E\u05DF\u026Fuuodb\u0279s\u0287n\u028C\u028Dx\u028Ez" +
			"\u2C6F\u15FA\u0186\u15E1\u018E\u2132\u2141HI\u017F\u029E\uA780WNO\u0500\u1F49\u1D1AS\u27D8\u2229\u039BMX\u028EZ" +
			"0\u0196\u1105\u0190\u3123\u03DB9\u312586)(][}{><\u25B7\u25C1\u203E'\u061B\u02D9\u00BF\u00A1\\/,"

	private val invertedCodePoints = Int2IntOpenHashMap(NORMAL.length).apply {
		for (index in NORMAL.indices) {
			put(NORMAL[index].code, INVERTED[index].code)
		}
	}

	private data class Token(val start: Int, val end: Int, val argument: Int)

	fun signature(text: String): Int2IntMap {
		val firstPercent = text.indexOf('%')
		if (firstPercent < 0) return Int2IntMaps.EMPTY_MAP

		val matcher = argumentPattern.matcher(text)
		if (!matcher.find(firstPercent)) return Int2IntMaps.EMPTY_MAP

		val signature = Int2IntOpenHashMap(2)
		var nextArgument = 1
		do {
			val index = matcher.start(1)
			val argument = if (index >= 0) {
				argumentIndex(text, index, matcher.end(1))
			} else if (text[matcher.start() + 1] == '%') {
				0
			} else {
				nextArgument++
			}
			signature.addTo(argument, 1)
		} while (matcher.find())
		return signature
	}

	fun convert(text: String, symbols: Collection<String>, convertRun: (String) -> String): String {
		val matcher = tokenPattern(symbols).matcher(text)
		if (!matcher.find()) return convertRun(text)

		val result = StringBuilder(text.length)
		var cursor = 0
		do {
			if (cursor < matcher.start()) result.append(convertRun(text.substring(cursor, matcher.start())))
			result.append(text, matcher.start(), matcher.end())
			cursor = matcher.end()
		} while (matcher.find())
		if (cursor < text.length) result.append(convertRun(text.substring(cursor)))
		return result.toString()
	}

	fun upsideDown(text: String, symbols: Collection<String>): String {
		val result = StringBuilder(text.length)
		val matcher = tokenPattern(symbols).matcher(text)
		if (!matcher.find()) {
			appendUpsideDown(text, 0, text.length, result)
			return result.toString()
		}

		val tokens = ArrayList<Token>(2)
		var start = 0
		var nextArgument = 1
		do {
			if (matcher.start(2) >= 0) {
				// Line separators and style changes delimit runs so they stay attached to the same text.
				reverseRun(text, start, matcher.start(), tokens, result)
				result.append(text, matcher.start(), matcher.end())
				tokens.clear()
				start = matcher.end()
			} else {
				val index = matcher.start(1)
				val argument = when {
					index >= 0 -> argumentIndex(text, index, matcher.end(1))
					text[matcher.start()] != '%' -> -1
					text[matcher.start() + 1] == '%' -> 0
					else -> nextArgument++
				}
				tokens.add(Token(matcher.start(), matcher.end(), argument))
			}
		} while (matcher.find())
		reverseRun(text, start, text.length, tokens, result)
		return result.toString()
	}

	private fun argumentIndex(text: String, start: Int, end: Int): Int {
		val index = Integer.parseInt(text, start, end, 10)
		require(index >= 1) { "Minecraft argument indices start at 1" }
		return index
	}

	private fun tokenPattern(symbols: Collection<String>): Pattern {
		if (symbols.isEmpty()) return protectedPattern
		val units = symbols.sortedByDescending(String::length).joinToString("|", transform = Pattern::quote)
		return Pattern.compile("$PROTECTED|(?<!\\p{L})(?:$units)(?!\\p{L})")
	}

	private fun reverseRun(text: String, start: Int, end: Int, tokens: List<Token>, result: StringBuilder) {
		var cursor = end
		for (index in tokens.indices.reversed()) {
			val token = tokens[index]
			appendUpsideDown(text, token.end, cursor, result)
			if (token.argument > 0) {
				result.append('%').append(token.argument).append($$"$s")
			} else {
				result.append(text, token.start, token.end)
			}
			cursor = token.start
		}
		appendUpsideDown(text, start, cursor, result)
	}

	private fun appendUpsideDown(text: String, start: Int, end: Int, result: StringBuilder) {
		var cursor = end
		while (cursor > start) {
			val codePoint = text.codePointBefore(cursor)
			cursor -= Character.charCount(codePoint)
			result.appendCodePoint(invertedCodePoints.getOrDefault(codePoint, codePoint))
		}
	}
}
