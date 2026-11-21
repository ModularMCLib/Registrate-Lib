package com.modularmc.registrate.providers.core

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.RegistrateAdvancementProvider
import com.modularmc.registrate.providers.RegistrateDataMapProvider
import com.modularmc.registrate.providers.RegistrateDatapackProvider
import com.modularmc.registrate.providers.RegistrateGenericProvider
import com.modularmc.registrate.providers.RegistrateItemTagsProvider
import com.modularmc.registrate.providers.RegistrateLangProvider
import com.modularmc.registrate.providers.RegistrateTagsProvider
import com.modularmc.registrate.providers.RegistrateTagsProvider.IntrinsicTags
import com.modularmc.registrate.providers.RegistrateTagsProvider.KeyTags
import com.modularmc.registrate.providers.generators.RegistrateBlockModelGenerator
import com.modularmc.registrate.providers.generators.RegistrateItemModelGenerator
import com.modularmc.registrate.providers.generators.RegistrateModelProvider
import com.modularmc.registrate.providers.generators.RegistrateRecipeProvider
import com.modularmc.registrate.providers.generators.RegistrateRecipeRunner
import com.modularmc.registrate.providers.loot.RegistrateLootTableProvider
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.PackOutput
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.neoforged.fml.LogicalSide
import net.neoforged.neoforge.data.loading.DatagenModLoader

import java.util.concurrent.CompletableFuture
import java.util.function.Function

/** A typed key and factory for a provider created during GatherData. */
fun interface ProviderType<T : RegistrateProvider> : GeneratorType<T> {
	/** Creates this provider with lookups and already-constructed dependencies from this DataGen run. */
	fun create(context: Context<T>): T

	@Suppress("UNCHECKED_CAST")
	fun cast(provider: RegistrateProvider): T = provider as T

	fun <R : Any> createGenerator(type: String): GeneratorType<R> = object : GeneratorType<R> {
		override fun toString(): String = type
	}

	@JvmRecord
	data class Context<T : RegistrateProvider>(
		val type: ProviderType<T>,
		val parent: AbstractRegistrate<*>,
		val existing: Map<ProviderType<*>, RegistrateProvider>,
		val output: PackOutput,
		val provider: CompletableFuture<HolderLookup.Provider>,
	) {
		fun <R : RegistrateProvider> require(other: ProviderType<R>): R = other.cast(existing.getValue(other))
	}

	/** Creates a server provider from its owning Registrate, output and registry lookups. */
	fun interface SimpleServerDataFactory<T : RegistrateProvider> : ProviderType<T> {
		fun create(parent: AbstractRegistrate<*>, output: PackOutput, provider: CompletableFuture<HolderLookup.Provider>): T

		override fun create(context: Context<T>): T = create(context.parent, context.output, context.provider)

		fun asProvider(): ProviderType<T> = this
	}

	companion object {
		@JvmField
		val DYNAMIC = registerServerData("dynamic", ::RegistrateDatapackProvider)

		@JvmField
		val DATA_MAP = registerServerData("data_map", ::RegistrateDataMapProvider)

		@JvmField
		val RECIPE_RUNNER = registerServerData("recipe_runner", ::RegistrateRecipeRunner)

		@JvmField
		val ADVANCEMENT = registerServerData("advancement", ::RegistrateAdvancementProvider)

		@JvmField
		val LOOT = registerServerData("loot", ::RegistrateLootTableProvider)

		@JvmField
		val BLOCK_TAGS = registerIntrinsicTag("tags/block", "blocks", Registries.BLOCK, BuiltInRegistries.BLOCK)

		@JvmField
		val ENCHANTMENT_TAGS = registerDynamicTag("tags/enchantment", "enchantments", Registries.ENCHANTMENT)

		@JvmField
		val ITEM_TAGS = registerTag<Item, RegistrateItemTagsProvider>(
			"tags/item",
			Registries.ITEM,
		) {
			RegistrateItemTagsProvider(
				it.parent,
				it.type,
				"items",
				it.output,
				it.provider,
				it.require(BLOCK_TAGS).contentsGetter(),
			)
		}

		@JvmField
		val FLUID_TAGS = registerIntrinsicTag("tags/fluid", "fluids", Registries.FLUID, BuiltInRegistries.FLUID)

		@JvmField
		val ENTITY_TAGS = registerIntrinsicTag("tags/entity", "entity_types", Registries.ENTITY_TYPE, BuiltInRegistries.ENTITY_TYPE)

		@JvmField
		val GENERIC_SERVER = registerProvider(
			"registrate_generic_server_provider",
		) {
			RegistrateGenericProvider(it.parent, it.output, it.provider, LogicalSide.SERVER, it.type)
		}

		@JvmField
		val MODEL = registerClientProvider<RegistrateModelProvider>("model") {
			ProviderType { RegistrateModelProvider(it.parent, it.output) }
		}

		@JvmField
		val LANG = registerClientProvider<RegistrateLangProvider>("lang") {
			ProviderType { RegistrateLangProvider(it.parent, it.output) }
		}

		@JvmField
		val GENERIC_CLIENT = registerClientProvider<RegistrateGenericProvider>("registrate_generic_client_provider") {
			ProviderType { RegistrateGenericProvider(it.parent, it.output, it.provider, LogicalSide.CLIENT, it.type) }
		}

		@JvmField
		val BLOCKSTATE = MODEL.createGenerator<RegistrateBlockModelGenerator>("blockstate")

		@JvmField
		val ITEM_MODEL = MODEL.createGenerator<RegistrateItemModelGenerator>("item_model")

		@JvmField
		val RECIPE = RECIPE_RUNNER.createGenerator<RegistrateRecipeProvider>("recipe")

		@JvmStatic
		fun <T : RegistrateProvider> registerServerData(name: String, factory: SimpleServerDataFactory<T>): ProviderType<T> = registerProvider(name, factory.asProvider())

		@JvmStatic
		fun <T : RegistrateProvider> registerProvider(name: String, type: ProviderType<T>): ProviderType<T> {
			RegistrateDataProvider.TYPES[name] = type
			return type
		}

		@JvmStatic
		fun <T : RegistrateProvider> registerClientProvider(name: String, supplier: NonNullSupplier<ProviderType<T>>): ProviderType<T> {
			val type = if (DatagenModLoader.isRunningDataGen()) {
				supplier.get()
			} else {
				ProviderType {
					error("Client datagen provider '$name' is unavailable outside datagen")
				}
			}
			RegistrateDataProvider.CLIENT_TYPES.add(type)
			return registerProvider(name, type)
		}

		@JvmStatic
		@Suppress("UNCHECKED_CAST")
		fun <T : Any, R : RegistrateTagsProvider<T>> registerTag(name: String, key: ResourceKey<out Registry<T>>, type: ProviderType<R>): ProviderType<R> {
			val existing = RegistrateDataProvider.TAG_TYPES.putIfAbsent(key, type)
			return if (existing == null) registerProvider(name, type) else existing as ProviderType<R>
		}

		@JvmStatic
		fun <T : Any> registerIntrinsicTag(providerName: String, typeName: String, registryKey: ResourceKey<out Registry<T>>, registry: Registry<T>): ProviderType<IntrinsicTags<T>> {
			val key = Function<T, ResourceKey<T>> { value ->
				registry.getResourceKey(value).orElseThrow { IllegalStateException("Cannot generate tags for unregistered $typeName entry: $value") }
			}
			return registerTag(providerName, registryKey) { IntrinsicTags(it.parent, it.type, typeName, it.output, registryKey, it.provider, key) }
		}

		@JvmStatic
		fun <T : Any> registerDynamicTag(providerName: String, typeName: String, registry: ResourceKey<Registry<T>>): ProviderType<KeyTags<T>> =
			registerTag(providerName, registry) { KeyTags(it.parent, it.type, typeName, it.output, registry, it.provider) }

		@JvmStatic
		fun <T : RegistrateProvider> create(
			type: ProviderType<T>,
			parent: AbstractRegistrate<*>,
			existing: Map<ProviderType<*>, RegistrateProvider>,
			output: PackOutput,
			provider: CompletableFuture<HolderLookup.Provider>,
		): T = type.create(Context(type, parent, existing, output, provider))
	}
}
