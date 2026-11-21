package com.modularmc.registrate.providers.core

import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.export.LocalizationDocument
import com.modularmc.registrate.localization.export.LocalizationExportOutput
import com.modularmc.registrate.localization.resolution.TranslationRegistry

import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.neoforged.neoforge.data.event.GatherDataEvent

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/** Groups the providers attached to the same output before NeoForge starts the DataGen run. */
internal class DataGenBatch private constructor(private val output: PackOutput, private val root: Path) : DataProvider {
	private val participants = ArrayList<RegistrateDataProvider>()

	override fun run(cache: CachedOutput): CompletableFuture<*> {
		synchronized(pending) { pending.remove(root) }
		participants.sortBy(RegistrateDataProvider::getName)
		return run(cache, output, participants)
	}

	override fun getName(): String = "Registrate data and languages"

	companion object {
		private val pending = Object2ObjectOpenHashMap<Path, DataGenBatch>()

		fun attach(event: GatherDataEvent, provider: RegistrateDataProvider) {
			val output = event.generator.packOutput
			val root = output.outputFolder.toAbsolutePath().normalize()
			synchronized(pending) {
				val batch = pending.getOrPut(root) { event.addProvider(DataGenBatch(output, root)) }
				batch.participants.add(provider)
			}
		}

		fun run(cache: CachedOutput, output: PackOutput, participants: List<RegistrateDataProvider>): CompletableFuture<*> {
			val runs = Array(participants.size) { participants[it].runResources(cache) }
			return CompletableFuture.allOf(*runs).thenCompose {
				val translations = TranslationRegistry()
				val exportRegistries = Object2ObjectLinkedOpenHashMap<String, TranslationRegistry>()
				val exportLocales = Object2ObjectOpenHashMap<String, MinecraftLocale>()
				for (provider in participants) {
					if (!provider.hasLanguageProvider() || !provider.requestsLocalizationExport()) continue
					val modId = provider.modId
					val locale = provider.localizationSourceLocale()
					val previousLocale = exportLocales.putIfAbsent(modId, locale)
					check(previousLocale == null || previousLocale == locale) { "[$modId] Registrate instances use different source locales" }
					exportRegistries.computeIfAbsent(modId) { TranslationRegistry(translations) }
				}
				for (provider in participants) provider.collectTranslations(translations, exportRegistries[provider.modId])

				val exports = ObjectArrayList<LocalizationDocument>(exportRegistries.size)
				val iterator = exportRegistries.object2ObjectEntrySet().fastIterator()
				while (iterator.hasNext()) {
					val entry = iterator.next()
					exports.add(entry.value.export(entry.key, exportLocales.getValue(entry.key)))
				}
				val writes = ObjectArrayList<CompletableFuture<*>>(exports.size + 1)
				writes.add(LanguageOutput.write(cache, output, translations))
				for (document in exports) writes.add(LocalizationExportOutput.write(cache, output, document))
				CompletableFuture.allOf(*writes.toTypedArray())
			}
		}
	}
}
