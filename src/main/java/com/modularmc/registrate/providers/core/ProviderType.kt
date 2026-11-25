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

/** 数据提供器的类型键，同时负责在收集数据生成任务时创建提供器。 */
fun interface ProviderType<T : RegistrateProvider> : GeneratorType<T> {
	/**
	 * 创建本轮数据生成使用的提供器。
	 *
	 * @param context 所属 Registrate、输出位置、注册表查询和本轮已创建的依赖
	 * @return 由本类型创建的提供器
	 */
	fun create(context: Context<T>): T

	/** 按此类型键取得具体提供器。传入的实例应由同一类型键创建。 */
	@Suppress("UNCHECKED_CAST")
	fun cast(provider: RegistrateProvider): T = provider as T

	/** 创建供子生成器回调使用的类型键。数据生成回调应复用返回的同一个键。 */
	fun <R : Any> createGenerator(type: String): GeneratorType<R> = object : GeneratorType<R> {
		override fun toString(): String = type
	}

	/** 本轮创建数据提供器时传入的上下文，包括所属模组、输出位置、异步注册表查询和已创建的依赖。 */
	@JvmRecord
	data class Context<T : RegistrateProvider>(
		val type: ProviderType<T>,
		val parent: AbstractRegistrate<*>,
		val existing: Map<ProviderType<*>, RegistrateProvider>,
		val output: PackOutput,
		val provider: CompletableFuture<HolderLookup.Provider>,
	) {
		/** 获取本轮已经创建的前置提供器。所需的类型键不在上下文中时抛出 [NoSuchElementException]。 */
		fun <R : RegistrateProvider> require(other: ProviderType<R>): R = other.cast(existing.getValue(other))
	}

	/** 使用所属 Registrate、输出位置和注册表查询创建服务端数据提供器。 */
	fun interface SimpleServerDataFactory<T : RegistrateProvider> : ProviderType<T> {
		/** 用所属模组、输出位置和异步注册表查询创建服务端提供器。 */
		fun create(parent: AbstractRegistrate<*>, output: PackOutput, provider: CompletableFuture<HolderLookup.Provider>): T

		/** 从通用上下文提取服务端工厂所需的参数后创建提供器。 */
		override fun create(context: Context<T>): T = create(context.parent, context.output, context.provider)

		/** 将此工厂作为可登记的提供器类型键使用。 */
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

		/** 以 [name] 登记服务端数据提供器工厂，返回用于配置生成回调的类型键。 */
		@JvmStatic
		fun <T : RegistrateProvider> registerServerData(name: String, factory: SimpleServerDataFactory<T>): ProviderType<T> = registerProvider(name, factory.asProvider())

		/** 将提供器类型与 [name] 绑定。生成回调须使用返回的类型键。 */
		@JvmStatic
		fun <T : RegistrateProvider> registerProvider(name: String, type: ProviderType<T>): ProviderType<T> {
			RegistrateDataProvider.TYPES[name] = type
			return type
		}

		/** 将 [name] 重新绑定到 [type]，替换该名称原有的类型，并移除 [type] 的旧名称。 */
		@JvmStatic
		fun <T : RegistrateProvider> replaceProvider(name: String, type: ProviderType<T>): ProviderType<T> {
			RegistrateDataProvider.TYPES.forcePut(name, type)
			return type
		}

		/** 登记客户端提供器。实际工厂仅在数据生成启动中取得，以免普通运行加载生成器所依赖的类。 */
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

		/** 为指定注册表登记标签提供器。如果已有该注册表的提供器，返回现有类型键。 */
		@JvmStatic
		@Suppress("UNCHECKED_CAST")
		fun <T : Any, R : RegistrateTagsProvider<T>> registerTag(name: String, key: ResourceKey<out Registry<T>>, type: ProviderType<R>): ProviderType<R> {
			val existing = RegistrateDataProvider.TAG_TYPES.putIfAbsent(key, type)
			return if (existing == null) registerProvider(name, type) else existing as ProviderType<R>
		}

		/** 为可直接取得注册对象的注册表登记标签提供器。对象未注册时生成失败。 */
		@JvmStatic
		fun <T : Any> registerIntrinsicTag(providerName: String, typeName: String, registryKey: ResourceKey<out Registry<T>>, registry: Registry<T>): ProviderType<IntrinsicTags<T>> {
			val key = Function<T, ResourceKey<T>> { value ->
				registry.getResourceKey(value).orElseThrow { IllegalStateException("Cannot generate tags for unregistered $typeName entry: $value") }
			}
			return registerTag(providerName, registryKey) { IntrinsicTags(it.parent, it.type, typeName, it.output, registryKey, it.provider, key) }
		}

		/** 为按注册表键引用条目的动态注册表登记标签提供器。 */
		@JvmStatic
		fun <T : Any> registerDynamicTag(providerName: String, typeName: String, registry: ResourceKey<Registry<T>>): ProviderType<KeyTags<T>> =
			registerTag(providerName, registry) { KeyTags(it.parent, it.type, typeName, it.output, registry, it.provider) }

		/** 组合所属模组、输出位置、已有依赖及查询，创建指定类型的数据提供器。 */
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
