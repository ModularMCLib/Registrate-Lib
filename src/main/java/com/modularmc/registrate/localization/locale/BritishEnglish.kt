package com.modularmc.registrate.localization.locale

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

import java.util.regex.Pattern

internal object BritishEnglish {
	private val words = Object2ObjectOpenHashMap<String, String>(42).apply {
		put("color", "colour")
		put("colors", "colours")
		put("colored", "coloured")
		put("coloring", "colouring")
		put("colorize", "colourise")
		put("colorized", "colourised")
		put("colorizes", "colourises")
		put("colorizing", "colourising")
		put("favor", "favour")
		put("favors", "favours")
		put("favorite", "favourite")
		put("favorites", "favourites")
		put("honor", "honour")
		put("honors", "honours")
		put("honored", "honoured")
		put("honoring", "honouring")
		put("labor", "labour")
		put("neighbor", "neighbour")
		put("neighbors", "neighbours")
		put("rumor", "rumour")
		put("center", "centre")
		put("centers", "centres")
		put("centered", "centred")
		put("centering", "centring")
		put("meter", "metre")
		put("meters", "metres")
		put("liter", "litre")
		put("liters", "litres")
		put("theater", "theatre")
		put("theaters", "theatres")
		put("gray", "grey")
		put("grays", "greys")
		put("defense", "defence")
		put("offense", "offence")
		put("organize", "organise")
		put("organizes", "organises")
		put("organized", "organised")
		put("organizing", "organising")
		put("analyze", "analyse")
		put("analyzes", "analyses")
		put("analyzed", "analysed")
		put("analyzing", "analysing")
	}
	private val wordsPattern = Pattern.compile("[A-Za-z]+")

	fun convert(text: String): String {
		val matcher = wordsPattern.matcher(text)
		var converted: StringBuilder? = null
		var copiedUntil = 0
		while (matcher.find()) {
			val word = matcher.group()
			val replacement = words[word.lowercase()] ?: continue
			val output = converted ?: StringBuilder(text.length).also { converted = it }
			output.append(text, copiedUntil, matcher.start())
			output.append(caseLike(word, replacement))
			copiedUntil = matcher.end()
		}
		val output = converted ?: return text
		return output.append(text, copiedUntil, text.length).toString()
	}

	private fun caseLike(word: String, replacement: String): String = when {
		word.all(Char::isUpperCase) -> replacement.uppercase()
		word.first().isUpperCase() -> replacement.replaceFirstChar(Char::uppercase)
		else -> replacement
	}
}
