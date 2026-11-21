package com.modularmc.registrate.localization.resolution

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntMaps
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap

import java.util.regex.Pattern

internal object MinecraftTextFormat {
	private val tokenPattern = Pattern.compile("%(?:(\\d+)\\$)?s|%%")
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

	private val plainTextSignature: Int2IntMap = Int2IntMaps.EMPTY_MAP
	private val plainText = Parsed(plainTextSignature, null)

	class Parsed(val signature: Int2IntMap, val variant: String?)

	fun parse(text: String, invert: Boolean): Parsed {
		val firstPercent = text.indexOf('%')
		if (firstPercent < 0) {
			return parsePlain(text, invert)
		}

		val matcher = tokenPattern.matcher(text)
		if (!matcher.find(firstPercent)) {
			return parsePlain(text, invert)
		}

		val tokens = if (invert) ArrayList<Token>(2) else null
		val signature = Int2IntOpenHashMap(2)
		var nextArgument = 1
		do {
			val indexStart = matcher.start(1)
			val argument = when {
				text[matcher.start() + 1] == '%' -> 0

				indexStart < 0 -> nextArgument++

				else -> {
					val position = Integer.parseInt(text, indexStart, matcher.end(1), 10)
					require(position >= 1) { "Minecraft argument indices start at 1" }
					position
				}
			}
			signature.addTo(argument, 1)
			tokens?.add(Token(matcher.start(), matcher.end(), argument))
		} while (matcher.find())
		return Parsed(signature, tokens?.let { upsideDown(text, it) })
	}

	private fun parsePlain(text: String, invert: Boolean): Parsed = if (invert) Parsed(plainTextSignature, upsideDown(text, emptyList())) else plainText

	private fun upsideDown(text: String, tokens: List<Token>): String {
		val result = StringBuilder(text.length)
		var cursor = text.length
		for (index in tokens.indices.reversed()) {
			val token = tokens[index]
			appendUpsideDown(text, token.end, cursor, result)
			if (token.argument == 0) {
				result.append("%%")
			} else {
				result.append('%').append(token.argument).append($$"$s")
			}
			cursor = token.start
		}
		appendUpsideDown(text, 0, cursor, result)
		return result.toString()
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
