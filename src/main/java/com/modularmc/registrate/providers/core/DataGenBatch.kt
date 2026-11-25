package com.modularmc.registrate.providers.core

import com.modularmc.registrate.localization.MinecraftLocale
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

/** 在数据生成开始前，归集写入同一输出位置的提供器。 */
internal class DataGenBatch private constructor(private val output: PackOutput, private val root: Path) : DataProvider {
	private val participants = ObjectArrayList<RegistrateDataProvider>()

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
				val exportPaths = Object2ObjectOpenHashMap<String, Path>()
				val exportOwners = Object2ObjectOpenHashMap<Path, String>()
				for (provider in participants) {
					if (!provider.hasLanguageProvider()) continue
					val path = provider.localizationExportPath() ?: continue
					val modId = provider.modId
					val locale = provider.localizationSourceLocale()
					val previousLocale = exportLocales.putIfAbsent(modId, locale)
					check(previousLocale == null || previousLocale == locale) { "[$modId] Registrate instances use different source locales" }
					val previousPath = exportPaths.putIfAbsent(modId, path)
					check(previousPath == null || previousPath == path) { "[$modId] Registrate instances use different localization export paths" }
					val previousOwner = exportOwners.putIfAbsent(path, modId)
					check(previousOwner == null || previousOwner == modId) { "Localization exports for $previousOwner and $modId use the same path $path" }
					exportRegistries.computeIfAbsent(modId) { TranslationRegistry(translations) }
				}
				for (provider in participants) provider.collectTranslations(translations, exportRegistries[provider.modId])

				val writes = ObjectArrayList<CompletableFuture<*>>(exportRegistries.size + 1)
				writes.add(LanguageOutput.write(cache, output, translations))
				val iterator = exportRegistries.object2ObjectEntrySet().fastIterator()
				while (iterator.hasNext()) {
					val entry = iterator.next()
					val document = entry.value.export(entry.key, exportLocales.getValue(entry.key))
					writes.add(LocalizationExportOutput.write(cache, exportPaths.getValue(entry.key), document))
				}
				CompletableFuture.allOf(*writes.toTypedArray())
			}
		}
	}
}
