package com.modularmc.registrate.localization.locale

import com.ibm.icu.text.Transliterator

internal object TraditionalChinese {
	private val transliterator = ThreadLocal.withInitial { Transliterator.getInstance("Simplified-Traditional") }

	fun convert(text: String): String = transliterator.get().transliterate(text)
}
