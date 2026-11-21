package com.modularmc.registrate.providers.core

import com.modularmc.registrate.localization.resolution.TranslationRegistry

import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.neoforged.neoforge.data.event.GatherDataEvent

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

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
				for (provider in participants) provider.collectTranslations(translations)
				LanguageOutput.write(cache, output, translations)
			}
		}
	}
}
