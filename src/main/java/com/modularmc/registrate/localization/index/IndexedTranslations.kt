package com.modularmc.registrate.localization.index

import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.resolution.TranslationRegistry

import net.neoforged.fml.ModList

import java.io.DataInputStream
import java.io.IOException
import java.io.UncheckedIOException

/** Loads the declarations compiled into a mod's own resources during DataGen. */
object IndexedTranslations {
	@JvmStatic
	fun collect(modId: String, locale: MinecraftLocale, registry: TranslationRegistry) {
		val contents = ModList.get().getModFileById(modId).file.contents
		for (frontend in LocalizationIndex.Frontend.entries) {
			val index = LocalizationIndex.path(modId, frontend)

			try {
				val stream = contents.openFile(index) ?: continue
				DataInputStream(stream.buffered()).use { input ->
					LocalizationIndex.read(input) { entry ->
						registry.add(
							TranslationKey(entry.namespace, entry.key),
							locale,
							LocalizationDeclaration(entry.source, entry.translations, emptyList(), null, emptyList()),
							TranslationSource(TranslationSource.Kind.DECLARED, entry.location),
						)
					}
				}
			} catch (error: IOException) {
				throw UncheckedIOException("[$modId] Reading localization index $index", error)
			}
		}
	}
}
