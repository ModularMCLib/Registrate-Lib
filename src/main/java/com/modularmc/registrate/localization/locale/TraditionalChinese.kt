package com.modularmc.registrate.localization.locale

import com.ibm.icu.text.Transliterator

internal object TraditionalChinese {
	// OpenCC dictionaries are pinned to 1.4.2 (025f371dc76b598d77384fbdab90c937471844d8).
	private val hongKong = regionalTransliterator("zh_hk", "HKVariantsPhrases", "HKVariants", "HKIcuVariants")
	private val taiwan = regionalTransliterator("zh_tw", "TWPhrases", "TWVariantsPhrases", "TWVariants")

	fun toHongKong(text: String): String = hongKong.get().transliterate(text)

	fun toTaiwan(text: String): String = taiwan.get().transliterate(text)

	private fun regionalTransliterator(locale: String, vararg dictionaries: String): ThreadLocal<Transliterator> = ThreadLocal.withInitial {
		val mappings = LinkedHashMap<String, String>()
		for (dictionary in dictionaries) {
			val path = "/opencc/$dictionary.txt"
			val stream = checkNotNull(TraditionalChinese::class.java.getResourceAsStream(path)) { "Missing Chinese conversion dictionary: $path" }
			stream.bufferedReader().useLines { lines ->
				for (line in lines) {
					if (line.isBlank() || line.startsWith('#')) continue
					val (source, alternatives) = line.split('\t', limit = 2)
					mappings.putIfAbsent(source, alternatives.substringBefore(' '))
				}
			}
		}
		val rules = buildString {
			appendLine("::Simplified-Traditional;")
			// Match whole phrases and exceptions before their individual characters.
			for ((source, target) in mappings.entries.sortedByDescending { it.key.length }) {
				append('\'').append(source.replace("'", "''")).append("' > '")
				append(target.replace("'", "''")).appendLine("';")
			}
		}
		Transliterator.createFromRules(locale, rules, Transliterator.FORWARD)
	}
}
