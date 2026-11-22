package com.modularmc.registrate.localization.export

import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList

internal class LocalizationDocument(val modId: String, val sourceLocale: MinecraftLocale, val entries: ObjectArrayList<Entry>) {
	internal class Entry(
		val key: TranslationKey,
		val sourceText: String,
		val sourceRevision: String,
		val domains: ObjectArrayList<String>,
		val type: String?,
		val glossaryIds: ObjectArrayList<String>,
		val unitIds: ObjectArrayList<String>,
		val placeholderSignature: Int2IntMap,
		val translations: ObjectArrayList<Translation>,
	)

	internal class Translation(val locale: MinecraftLocale, val text: String, val source: TranslationSource, val sourceRevision: String, val status: String?, val locked: Boolean)
}
