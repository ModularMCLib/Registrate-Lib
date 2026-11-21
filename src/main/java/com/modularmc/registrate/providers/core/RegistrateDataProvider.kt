package com.modularmc.registrate.providers.core

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.internal.util.DebugMarkers
import com.modularmc.registrate.internal.util.RegistrateLogger
import com.modularmc.registrate.localization.resolution.TranslationRegistry
import com.modularmc.registrate.providers.RegistrateLangProvider
import com.modularmc.registrate.providers.RegistrateTagsProvider

import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.resources.ResourceKey
import net.neoforged.fml.LogicalSide
import net.neoforged.neoforge.data.event.GatherDataEvent

import com.google.common.collect.HashBiMap
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

open class RegistrateDataProvider(private val parent: AbstractRegistrate<*>, private val mod: String, event: GatherDataEvent) : DataProvider {
	private val output = event.generator.packOutput
	private val registriesLookup: CompletableFuture<HolderLookup.Provider> = event.lookupProvider
	private val subProviders = Object2ObjectLinkedOpenHashMap<ProviderType<*>, RegistrateProvider>()
	private val prerequisites = Object2ObjectOpenHashMap<ProviderType<*>, ProviderType<*>>()
	private val subGenerators = ConcurrentHashMap<GeneratorType<*>, Any>()

	init {
		LOGGER.debug(DebugMarkers.DATA, RegistrateLogger.modMessage("Collecting datagen providers"), mod)
		val sortedProviders = parent.dataGenInitializer.getSortedProviders()
		val known = Object2ObjectOpenHashMap<ProviderType<*>, RegistrateProvider>(sortedProviders.size)
		val includeClient = event is GatherDataEvent.Client
		for ((id, type, prerequisite) in sortedProviders) {
			if (type in CLIENT_TYPES && !includeClient) continue

			val dependency = known[prerequisite] as? RegistrateLookupFillerProvider
			val lookup = dependency?.getFilledProvider() ?: registriesLookup
			val provider = ProviderType.create(type, parent, known, output, lookup)
			check(provider !is RegistrateTagsProvider<*> || TAG_TYPES[provider.registry()] === type) {
				"Tag providers must be registered through ProviderType::registerTag"
			}
			known[type] = provider
			val enabled = provider.getSide() == LogicalSide.SERVER || includeClient
			if (enabled) {
				if (prerequisite != null) {
					check(subProviders.containsKey(prerequisite)) { "[$mod] Provider $id requires disabled provider ${getTypeName(prerequisite)}" }
					prerequisites[type] = prerequisite
				}
				LOGGER.debug(DebugMarkers.DATA, RegistrateLogger.modMessage("Added datagen provider {}"), mod, id)
				subProviders[type] = provider
			}
		}
	}

	override fun run(cache: CachedOutput): CompletableFuture<*> = DataGenBatch.run(cache, output, listOf(this))

	open fun attach(event: GatherDataEvent) {
		DataGenBatch.attach(event, this)
	}

	internal fun runResources(cache: CachedOutput): CompletableFuture<*> = registriesLookup.thenCompose {
		val runs = Object2ObjectOpenHashMap<ProviderType<*>, CompletableFuture<*>>(subProviders.size)
		val iterator = subProviders.object2ObjectEntrySet().fastIterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			val type = entry.key
			if (type === ProviderType.LANG) continue

			val provider = entry.value
			val dependency = prerequisites[type]
			runs[type] = if (dependency == null) provider.run(cache) else runs.getValue(dependency).thenCompose { provider.run(cache) }
		}
		CompletableFuture.allOf(*runs.values.toTypedArray())
	}

	internal val modId: String
		get() = mod

	internal fun hasLanguageProvider(): Boolean = ProviderType.LANG in subProviders

	internal fun requestsLocalizationExport(): Boolean = parent.localizationExportRequested()

	internal fun localizationSourceLocale() = parent.requireSourceLocale()

	internal fun collectTranslations(registry: TranslationRegistry, exportRegistry: TranslationRegistry?) {
		(subProviders[ProviderType.LANG] as? RegistrateLangProvider)?.collect(exportRegistry ?: registry)
	}

	override fun getName(): String = "Registrate Provider for $mod"

	@Suppress("UNCHECKED_CAST")
	open fun <P : Any> getSubProvider(type: GeneratorType<P>): Optional<P> {
		val provider = if (type is ProviderType<*>) subProviders[type] else subGenerators[type]
		return Optional.ofNullable(provider as P?)
	}

	open fun <T : Any> putSubProvider(type: GeneratorType<out T>, generator: T) {
		if (type !is ProviderType<*>) subGenerators[type] = generator
	}

	companion object {
		private val LOGGER = RegistrateLogger.datagen()
		internal val TYPES = HashBiMap.create<String, ProviderType<*>>()
		internal val TAG_TYPES = ConcurrentHashMap<ResourceKey<out Registry<*>>, ProviderType<*>>()
		internal val CLIENT_TYPES = ObjectOpenHashSet<ProviderType<*>>()

		@JvmStatic
		fun getTypeName(type: GeneratorType<*>): String? = if (type is ProviderType<*>) TYPES.inverse()[type] else type.toString()
	}
}
