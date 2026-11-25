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

/** 按收集事件的逻辑侧和依赖顺序组织模组的数据提供器，向下游提供器传递正确的注册表查询。 */
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
		for ((id, type, prerequisite) in sortedProviders) {
			if (type in CLIENT_TYPES && !event.includeClient()) continue

			val dependency = known[prerequisite] as? RegistrateLookupFillerProvider
			val lookup = dependency?.getFilledProvider() ?: registriesLookup
			val provider = ProviderType.create(type, parent, known, output, lookup, event.existingFileHelper)
			check(provider !is RegistrateTagsProvider<*> || TAG_TYPES[provider.registry()] === type) {
				"Tag providers must be registered through ProviderType::registerTag"
			}
			known[type] = provider
			val enabled = if (provider.getSide() == LogicalSide.CLIENT) event.includeClient() else event.includeServer()
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

	/** 用当前模组的提供器独立执行一轮数据生成，并在资源生成后汇总语言文件。 */
	override fun run(cache: CachedOutput): CompletableFuture<*> = DataGenBatch.run(cache, output, listOf(this))

	/** 将本实例加入当前收集事件的生成批次，让写入同一输出目录的模组共享语言阶段。 */
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

	internal fun localizationExportPath() = parent.localizationExportPath()

	internal fun localizationSourceLocale() = parent.requireSourceLocale()

	internal fun collectTranslations(registry: TranslationRegistry, exportRegistry: TranslationRegistry?) {
		(subProviders[ProviderType.LANG] as? RegistrateLangProvider)?.collect(exportRegistry ?: registry)
	}

	/** 返回带有模组标识的生成任务名称。 */
	override fun getName(): String = "Registrate Provider for $mod"

	/** 查询本轮启用的提供器或已构造的子生成器。对应类型未启用或尚未创建时返回空值。 */
	@Suppress("UNCHECKED_CAST")
	open fun <P : Any> getSubProvider(type: GeneratorType<P>): Optional<P> {
		val provider = if (type is ProviderType<*>) subProviders[type] else subGenerators[type]
		return Optional.ofNullable(provider as P?)
	}

	/** 保存本轮创建的子生成器，供其他回调按类型键查询。提供器类型键由此对象统一管理。 */
	open fun <T : Any> putSubProvider(type: GeneratorType<out T>, generator: T) {
		if (type !is ProviderType<*>) subGenerators[type] = generator
	}

	companion object {
		private val LOGGER = RegistrateLogger.datagen()
		internal val TYPES = HashBiMap.create<String, ProviderType<*>>()
		internal val TAG_TYPES = ConcurrentHashMap<ResourceKey<out Registry<*>>, ProviderType<*>>()
		internal val CLIENT_TYPES = ObjectOpenHashSet<ProviderType<*>>()

		/** 返回已登记的提供器名称，未登记时返回 `null`。子生成器键使用其文本名称。 */
		@JvmStatic
		fun getTypeName(type: GeneratorType<*>): String? = if (type is ProviderType<*>) TYPES.inverse()[type] else type.toString()
	}
}
