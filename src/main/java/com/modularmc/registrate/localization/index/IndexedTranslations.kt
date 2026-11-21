package com.modularmc.registrate.localization.index

import com.modularmc.registrate.localization.DeclarationDefaults
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.resolution.TranslationRegistry
import com.modularmc.registrate.localization.terminology.Terminology

import net.neoforged.fml.ModList

import java.io.DataInputStream
import java.io.IOException
import java.io.UncheckedIOException

/** Loads the declarations compiled into a mod's own resources during DataGen. */
object IndexedTranslations {
	@JvmStatic
	@JvmOverloads
	fun collect(modId: String, locale: MinecraftLocale, registry: TranslationRegistry, terminology: Terminology = Terminology.EMPTY, defaults: DeclarationDefaults = DeclarationDefaults.EMPTY) {
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
							LocalizationDeclaration(entry.source, entry.translations, defaults.domains, defaults.type, defaults.glossary, defaults.glossaryIds, defaults.unitIds),
							TranslationSource(TranslationSource.Kind.DECLARED, entry.location),
							terminology,
						)
					}
				}
			} catch (error: IOException) {
				throw UncheckedIOException("[$modId] Reading localization index $index", error)
			}
		}
	}
}
