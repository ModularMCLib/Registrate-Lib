// 格式化时，KtLint 会根据行宽限制折叠转发用的重载。
@file:Suppress("ktlint:standard:max-line-length")

package com.modularmc.registrate

import com.modularmc.registrate.builders.BlockBuilder
import com.modularmc.registrate.builders.BlockEntityBuilder
import com.modularmc.registrate.builders.BlockEntityBuilder.BlockEntityFactory
import com.modularmc.registrate.builders.EntityBuilder
import com.modularmc.registrate.builders.FluidBuilder
import com.modularmc.registrate.builders.FluidBuilder.FluidFactory
import com.modularmc.registrate.builders.FluidBuilder.FluidTypeFactory
import com.modularmc.registrate.builders.ItemBuilder
import com.modularmc.registrate.builders.MenuBuilder
import com.modularmc.registrate.builders.MenuBuilder.ForgeMenuFactory
import com.modularmc.registrate.builders.MenuBuilder.MenuFactory
import com.modularmc.registrate.builders.MenuBuilder.ScreenFactory
import com.modularmc.registrate.builders.base.AbstractBuilder
import com.modularmc.registrate.builders.base.Builder
import com.modularmc.registrate.builders.base.BuilderCallback
import com.modularmc.registrate.builders.base.NoConfigBuilder
import com.modularmc.registrate.export.RegistrateExportBuilder
import com.modularmc.registrate.internal.RegistrationTracker
import com.modularmc.registrate.internal.event.OneTimeEventReceiver.Companion.addModListener
import com.modularmc.registrate.internal.lifecycle.RegistrateLifecycle
import com.modularmc.registrate.internal.util.DebugMarkers
import com.modularmc.registrate.internal.util.RegistrateLogger.core
import com.modularmc.registrate.internal.util.RegistrateLogger.datagen
import com.modularmc.registrate.internal.util.RegistrateLogger.modMessage
import com.modularmc.registrate.localization.DeclarationDefaults
import com.modularmc.registrate.localization.EntryLocalization
import com.modularmc.registrate.localization.ImportedTranslation
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.LocalizationDeclaration.Companion.sourceText
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.locale.LocaleDerivation
import com.modularmc.registrate.localization.resolution.TranslationRegistry
import com.modularmc.registrate.localization.terminology.Terminology
import com.modularmc.registrate.providers.RegistrateLangProvider.Companion.toEnglishName
import com.modularmc.registrate.providers.core.DataProviderInitializer
import com.modularmc.registrate.providers.core.GeneratorType
import com.modularmc.registrate.providers.core.RegistrateDataProvider
import com.modularmc.registrate.providers.core.RegistrateDataProvider.Companion.getTypeName
import com.modularmc.registrate.util.CreativeModeTabModifier
import com.modularmc.registrate.util.entry.EntryMetadata
import com.modularmc.registrate.util.entry.RegistryEntry
import com.modularmc.registrate.util.nullness.NonNullBiFunction
import com.modularmc.registrate.util.nullness.NonNullConsumer
import com.modularmc.registrate.util.nullness.NonNullFunction
import com.modularmc.registrate.util.nullness.NonNullSupplier
import com.modularmc.registrate.util.nullness.NonNullUnaryOperator

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.MenuAccess
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.util.Util
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityType.EntityFactory
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.Fluid
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.neoforged.neoforge.data.loading.DatagenModLoader
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.fluids.BaseFlowingFluid
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.NewRegistryEvent
import net.neoforged.neoforge.registries.RegisterEvent
import net.neoforged.neoforge.registries.RegistryBuilder

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.HashBasedTable
import com.mojang.serialization.Codec
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.apache.commons.lang3.tuple.Pair
import org.apache.logging.log4j.Level

import java.nio.file.Path
import java.util.EnumMap
import java.util.Optional
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.BiConsumer
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * 统一管理一个模组的条目注册和数据生成回调。
 *
 * 通过 [object] 设置当前条目名称。后续省略名称的构建入口会沿用它，直到再次调用 [object]。
 * 也可用 [block] 等带名称的重载直接指定条目名称，当前名称保持原值。
 * 构建过程持有可变状态，由模组初始化线程负责。
 *
 * ```java
 * public static final Registrate REGISTRATE = Registrate.create("mymod");
 * public static final BlockEntry<MyBlock> MY_BLOCK = REGISTRATE.object("my_block")
 *     .block(MyBlock::new)
 *     .simpleItem()
 *     .register();
 * ```
 *
 * 各类条目的默认值及数据生成入口见对应构建器。
 */
abstract class AbstractRegistrate<S : AbstractRegistrate<S>> protected constructor(
	/**
	 * 本实例管理的模组标识。创建注册条目时使用它作为命名空间。
	 */
	val modid: String,
) {
	private inner class LifecycleHooks : RegistrateLifecycle.Hooks {
		override fun modEventBus(): IEventBus? = modEventBus

		override fun setModEventBus(bus: IEventBus) {
			modEventBus = bus
		}

		override fun doDatagen(): Boolean = dataGeneration.get()

		override fun onRegister(event: RegisterEvent) {
			this@AbstractRegistrate.onRegister(event)
		}

		override fun onRegisterLate(event: RegisterEvent) {
			this@AbstractRegistrate.onRegisterLate(event)
		}

		override fun onBuildCreativeModeTabContents(event: BuildCreativeModeTabContentsEvent) {
			this@AbstractRegistrate.onBuildCreativeModeTabContents(event)
		}

		override fun onData(event: GatherDataEvent) {
			this@AbstractRegistrate.onData(event)
		}
	}

	private val callback = object : BuilderCallback {
		override fun <R : Any, T : R> accept(
			name: String,
			type: ResourceKey<out Registry<R>>,
			builder: Builder<R, T, *, *>,
			factory: NonNullSupplier<out T>,
			entryFactory: NonNullFunction<DeferredHolder<R, T>, out RegistryEntry<R, T>>,
		): RegistryEntry<R, T> = this@AbstractRegistrate.accept(name, type, builder, factory, entryFactory)
	}

	private val registrationTracker = RegistrationTracker(modid)
	private val lifecycle = RegistrateLifecycle(this, LifecycleHooks())

	private val datagensByEntry = HashBasedTable.create<Pair<String, ResourceKey<out Registry<*>>>, GeneratorType<*>, EntryMetadata.DataGenerator<*>>()
	private val datagens = ArrayListMultimap.create<GeneratorType<*>, NonNullConsumer<*>>()
	private val creativeModeTabModifiers = ArrayListMultimap.create<ResourceKey<CreativeModeTab>, Consumer<CreativeModeTabModifier>>()
	private var defaultCreativeModeTab: ResourceKey<CreativeModeTab> = CreativeModeTabs.SEARCH

	private val dataGeneration = NonNullSupplier.lazy(DatagenModLoader::isRunningDataGen)

	/** 返回延迟读取本次启动是否启用数据生成的取值器。首次查询后复用该结果。 */
	open fun doDatagen(): NonNullSupplier<Boolean> = dataGeneration

	/**
	 * 模组的事件总线，用于绑定注册及数据生成监听器。绑定前为空，扩展构建器可在绑定后用它登记监听器。
	 */

	var modEventBus: IEventBus? = null

	private lateinit var currentName: String
	private var skipErrors = false

	/**
	 * 把当前实例转换为子类声明的 [S]，便于扩展方法返回自身并继续链式调用。
	 *
	 * @return 当前实例，类型为 [S]
	 */
	@Suppress("UNCHECKED_CAST")
	protected fun self(): S = this as S

	/**
	 * 在 [Registrate.create] 绑定模组事件总线时初始化监听器。子类可以重写此方法添加监听器，需调用父类实现完成基础绑定。
	 *
	 * @param bus 要绑定的模组事件总线
	 * @return 当前实例
	 */
	open fun registerEventListeners(bus: IEventBus): S {
		lifecycle.register(bus)
		return self()
	}

	/**
	 * 每个注册表触发一次，将此前收集的条目提交到对应注册表。重写时须调用父类实现。
	 *
	 * @param event 当前注册事件，通过 [RegisterEvent.getRegistryKey] 取得注册表键
	 */
	protected open fun onRegister(event: RegisterEvent) {
		registrationTracker.onRegister(event, skipErrors, isDevEnvironment)
	}

	/**
	 * 以[最低优先级][net.neoforged.bus.api.EventPriority.LOWEST] 处理每个注册表的注册事件。
	 * 用于执行依赖当前注册表已完成注册的操作，包括依赖其他模组条目的操作。重写时须调用父类实现。
	 *
	 * @param event 当前注册事件，通过 [RegisterEvent.getRegistryKey] 取得注册表键
	 */
	protected open fun onRegisterLate(event: RegisterEvent) {
		registrationTracker.onRegisterLate(event)
	}

	/**
	 * 填充创造模式物品栏时触发，执行针对当前标签页的修改回调。
	 *
	 * @param event 当前标签页的填充事件
	 */
	protected open fun onBuildCreativeModeTabContents(event: BuildCreativeModeTabContentsEvent) {
		val modifier = CreativeModeTabModifier(
			Supplier(event::getFlags),
			BooleanSupplier(event::hasPermissions),
			BiConsumer(event::accept),
			Supplier(event::getParameters),
		)

		for (callback in creativeModeTabModifiers[event.tabKey]) {
			callback.accept(modifier)
		}
	}

	private var provider: RegistrateDataProvider? = null

	/**
	 * 数据生成开始时绑定本实例的生成器。扩展实现可重写此方法，为同一轮生成追加数据。
	 *
	 * @param event 本轮数据生成事件
	 */
	protected open fun onData(event: GatherDataEvent) {
		val dataProvider = RegistrateDataProvider(this, modid, event)
		provider = dataProvider
		dataProvider.attach(event)
	}

	/**
	 * 读取最近一次由 [object] 设置的条目名称。
	 *
	 * @return 当前条目名称
	 * @throws UninitializedPropertyAccessException 尚未设置当前名称
	 */
	protected open fun currentName(): String = currentName

	/**
	 * 在指定注册表中查找当前名称对应的条目。可用于取得构建链一并创建的同名条目，例如方块的物品。
	 *
	 * ```java
	 * public static final RegistryEntry<Item, BlockItem> MY_BLOCK_ITEM = REGISTRATE.object("my_block")
	 *     .block(MyBlock::new)
	 *     .simpleItem()
	 *     .lang("My Special Block")
	 *     .build()
	 *     .get(Registries.ITEM);
	 * ```
	 *
	 * @param R 注册表中的基础类型
	 * @param T 所需条目的具体类型
	 * @param type 注册表键
	 * @return 指定注册表中的 [RegistryEntry]，对象在注册绑定后可读
	 * @throws IllegalArgumentException 尚未提交对应条目
	 * @throws UninitializedPropertyAccessException 尚未通过 [object] 设置当前名称
	 */
	open fun <R : Any, T : R> get(type: ResourceKey<out Registry<R>>) = this.get<R, T>(currentName(), type)

	/**
	 * 按名称和注册表查找已提交的条目，也可取得构建器一并创建的附属条目。
	 *
	 * ```java
	 * public static final BlockEntry<MyBlock> MY_BLOCK = REGISTRATE.object("my_block")
	 *     .block(MyBlock::new)
	 *     .simpleItem()
	 *     .register();
	 * public static final RegistryEntry<Item, BlockItem> MY_BLOCK_ITEM = REGISTRATE.get("my_block", Registries.ITEM);
	 * ```
	 *
	 * @param R 注册表中的基础类型
	 * @param T 所需条目的具体类型
	 * @param name 本模组命名空间内的条目名称
	 * @param type 注册表键
	 * @return 指定的 [RegistryEntry]，对象在注册绑定后可读
	 * @throws IllegalArgumentException 尚未提交对应条目
	 */
	open fun <R : Any, T : R> get(name: String, type: ResourceKey<out Registry<R>>) = registrationTracker.get<R, T>(name, type)

	/**
	 * 按名称和注册表查找可选条目。流体注册会用此入口查询可能缺省的附属条目。
	 *
	 * @param R 注册表中的基础类型
	 * @param T 所需条目的具体类型
	 * @param name 本模组命名空间内的条目名称
	 * @param type 注册表键
	 * @return 找到时返回包含 [RegistryEntry] 的 [Optional]，查不到时返回空值
	 */
	open fun <R : Any, T : R> getOptional(name: String, type: ResourceKey<out Registry<R>>) = registrationTracker.getOptional<R, T>(name, type)

	/**
	 * 收集本实例已提交给指定注册表的条目。注册完成前也可查询，条目引用要等各自绑定后才能取得对象。
	 *
	 * @param R 注册表中的基础类型
	 * @param type 注册表键
	 * @return 该注册表中由本实例提交的 [RegistryEntry] 集合
	 */
	open fun <R : Any, T : R> getAll(type: ResourceKey<out Registry<R>>) = registrationTracker.getAll<R, T>(type)

	/**
	 * 为指定条目添加注册回调。条目写入注册表后立即执行，再继续注册后续条目。
	 *
	 * @param R 注册表中的基础类型
	 * @param T 条目类型
	 * @param name 本实例的 [modid] 命名空间内要监听的条目名称
	 * @param registryType 注册表键
	 * @param callback 接收已注册对象的回调
	 * @return 当前实例
	 */
	open fun <R : Any, T : R> addRegisterCallback(name: String, registryType: ResourceKey<out Registry<R>>, callback: NonNullConsumer<in T>): S {
		registrationTracker.addRegisterCallback(name, registryType, callback)
		return self()
	}

	/**
	 * 在指定注册表完成所有条目的注册后执行回调。
	 *
	 * @param R 注册表中的基础类型
	 * @param registryType 注册表键
	 * @param callback 注册表完成时执行的回调
	 * @return 当前实例
	 */
	open fun <R : Any> addRegisterCallback(registryType: ResourceKey<out Registry<R>>, callback: Runnable): S {
		registrationTracker.addAfterRegisterCallback(registryType, callback)
		return self()
	}

	/**
	 * 查询指定注册表是否完成注册。
	 *
	 * @param R 注册表中的基础类型
	 * @param registryType 注册表键
	 * @return 已完成注册时返回真
	 */
	open fun <R : Any> isRegistered(registryType: ResourceKey<out Registry<R>>) = registrationTracker.isRegistered(registryType)

	/**
	 * 在数据生成阶段查询指定类型的提供器。此入口供生成回调使用。
	 *
	 * @param P 提供器类型
	 * @param type 要查询的 [GeneratorType]
	 * @return 包含提供器的 [Optional]。本轮未启用对应侧的生成任务时可能为空
	 * @throws IllegalStateException 数据生成尚未开始
	 */
	open fun <P : Any> getDataProvider(type: GeneratorType<P>): Optional<P> {
		val provider = this.provider
		if (provider != null) {
			return provider.getSubProvider(type)
		}
		throw IllegalStateException("Cannot get data provider before datagen is started")
	}

	/**
	 * 为构建器的条目设置指定类型的数据生成回调。重复设置会替换该条目原有的同类型回调。
	 *
	 * @param P 提供器类型
	 * @param R 注册表中的基础类型
	 * @param builder 条目的构建器
	 * @param type 要生成的 [GeneratorType]
	 * @param cons 数据生成时执行的回调
	 * @return 当前实例
	 */
	open fun <P : Any, R : Any> setDataGenerator(builder: Builder<R, *, *, *>, type: GeneratorType<out P>, cons: NonNullConsumer<out P>) =
		this.setDataGenerator(builder.name, builder.registryKey, type, cons)

	/**
	 * 移除构建器的条目此前配置的指定类型数据生成回调。
	 *
	 * @param P 提供器类型
	 * @param R 注册表中的基础类型
	 * @param builder 条目的构建器
	 * @param type 要移除的 [GeneratorType]
	 * @return 当前实例
	 */
	open fun <P : Any, R : Any> removeDataGenerator(builder: Builder<R, *, *, *>, type: GeneratorType<out P>): S = this.removeDataGenerator(builder.name, builder.registryKey, type)

	/**
	 * 为指定条目和生成器类型设置回调。重复设置会替换该条目原有的同类型回调。
	 *
	 * @param P 提供器类型
	 * @param R 注册表中的基础类型
	 * @param entry 条目名称
	 * @param registryType 条目所属的注册表键
	 * @param type 要生成的 [GeneratorType]
	 * @param cons 数据生成时执行的回调
	 * @return 当前实例
	 */
	open fun <P : Any, R : Any> setDataGenerator(entry: String, registryType: ResourceKey<out Registry<R>>, type: GeneratorType<out P>, cons: NonNullConsumer<out P>) =
		setDataGenerator(entry, registryType, EntryMetadata.DataGenerator(type, cons))

	private fun <P : Any, R : Any> setDataGenerator(entry: String, registryType: ResourceKey<out Registry<R>>, generator: EntryMetadata.DataGenerator<P>): S {
		val existing = datagensByEntry.put(Pair.of(entry, registryType), generator.type, generator)
		if (existing != null) {
			datagens.remove(generator.type, existing.callback)
		}
		return addDataGenerator(generator.type, generator.callback)
	}

	/**
	 * 移除指定条目此前配置的同类型数据生成回调。
	 *
	 * @param P 提供器类型
	 * @param R 注册表中的基础类型
	 * @param entry 条目名称
	 * @param registryType 条目所属的注册表键
	 * @param type 要移除的 [GeneratorType]
	 * @return 当前实例
	 */
	open fun <P : Any, R : Any> removeDataGenerator(entry: String, registryType: ResourceKey<out Registry<R>>, type: GeneratorType<out P>): S {
		val existing = datagensByEntry.remove(Pair.of(entry, registryType), type)
		if (existing != null) {
			datagens.remove(type, existing.callback)
		}
		return self()
	}

	/**
	 * 追加独立于注册条目的数据生成回调，用于生成没有绑定条目的资源。重复调用会累加回调。
	 *
	 * @param T 提供器类型
	 * @param type 要生成的 [GeneratorType]
	 * @param cons 数据生成时执行的回调
	 * @return 当前实例
	 */
	open fun <T : Any> addDataGenerator(type: GeneratorType<out T>, cons: NonNullConsumer<out T>): S {
		if (dataGeneration.get()) {
			check(provider == null) { "Cannot add data generator after construction of root generator" }
			datagens.put(type, cons)
		}
		return self()
	}

	/** 将回调插入指定生成器类型的队首，使它先于同类型的其他回调执行。 */
	open fun <T : Any> addDataGeneratorFirst(type: GeneratorType<out T>, cons: NonNullConsumer<out T>): S {
		if (dataGeneration.get()) {
			check(provider == null) { "Cannot add data generator after construction of root generator" }
			datagens.get(type).addFirst(cons)
		}
		return self()
	}

	open val dataGenInitializer: DataProviderInitializer by lazy(LazyThreadSafetyMode.NONE, ::DataProviderInitializer)

	@JvmRecord
	private data class RawTranslation(val key: TranslationKey, val declaration: LocalizationDeclaration, val source: TranslationSource)

	private val extraLang = ArrayList<RawTranslation>()
	private val generatedLang = ConcurrentLinkedQueue<RawTranslation>()
	private val importedTranslations = ObjectArrayList<ImportedTranslation>()
	private var sourceLocale: MinecraftLocale? = null
	private var defaultLocalization = DeclarationDefaults.EMPTY
	private var localizationExportPath: Path? = null
	private val terminology = Terminology()
	private val localeDerivations = EnumMap<MinecraftLocale, LocaleDerivation>(MinecraftLocale::class.java)

	/** 在数据生成开始前配置本模组的源语言，供本地化声明解释源文本。 */
	open fun sourceLocale(locale: MinecraftLocale): S {
		sourceLocale = locale
		return self()
	}

	/** 开始配置本实例随数据生成写出的导出文件。 */
	open fun export(): RegistrateExportBuilder = RegistrateExportBuilder(this)

	internal fun requestLocalizationExport(path: Path) {
		localizationExportPath = path
	}

	internal fun localizationExportPath(): Path? = localizationExportPath

	/** 设置此后创建的本地化声明使用的默认值。 */
	open fun localizationDefaults(defaults: DeclarationDefaults): S {
		defaultLocalization = defaults
		return self()
	}

	internal fun localizationDefaultValues(): DeclarationDefaults = defaultLocalization

	/** 读取本模组的源语言。数据生成前须显式配置。 */
	open fun requireSourceLocale(): MinecraftLocale {
		val locale = sourceLocale
		requireNotNull(locale) { "[$modid] Configure sourceLocale before generating languages" }
		return locale
	}

	/** 返回当前模组的术语表，供初始化时登记领域、术语和单位。 */
	open fun terminology(): Terminology = terminology

	/** 在模组初始化期间登记外部译文，供后续客户端侧数据生成解析。 */
	open fun importTranslation(translation: ImportedTranslation): S {
		importedTranslations.add(translation)
		return self()
	}

	internal fun collectImportedTranslations(registry: TranslationRegistry, locale: MinecraftLocale) {
		val terminology = terminology()
		for (translation in importedTranslations) registry.addImported(locale, translation, terminology)
	}

	/** 指定数据生成补齐缺失译文的规则。初始化期间可替换同一目标语言的规则。 */
	open fun deriveLocale(derivation: LocaleDerivation): S {
		localeDerivations[derivation.output] = derivation
		return self()
	}

	internal fun configureLocaleDerivations(registry: TranslationRegistry) {
		if (localeDerivations.isNotEmpty()) registry.configureDerivations(terminology(), modid, localeDerivations.values)
	}

	internal fun collectRawTranslations(registry: TranslationRegistry, locale: MinecraftLocale) {
		for (translation in extraLang) {
			collectRawTranslation(registry, locale, translation)
		}
		while (true) {
			val translation = generatedLang.poll() ?: break
			collectRawTranslation(registry, locale, translation)
		}
	}

	private fun collectRawTranslation(registry: TranslationRegistry, locale: MinecraftLocale, translation: RawTranslation) {
		if (translation.source.kind != TranslationSource.Kind.AUTOMATIC || locale == MinecraftLocale.EN_US) {
			registry.add(translation.key, locale, translation.declaration, translation.source, terminology())
		}
	}

	/**
	 * 按原版的命名规则从类型和资源标识生成翻译键，再登记源文本。方块 `mymod:myblock` 的键是 `block.mymod.myblock`。
	 *
	 * @param type 翻译键的类型前缀
	 * @param id 对象的资源标识，由 [Util.makeDescriptionId] 转换
	 * @param localizedName 使用本模组源语言书写的名称
	 * @return 使用生成键的 [MutableComponent]
	 */
	open fun addLang(type: String, id: Identifier, localizedName: String): MutableComponent = addRawLang(Util.makeDescriptionId(type, id), localizedName)

	/**
	 * 按原版规则生成翻译键，并在键末尾以点号连接后缀，再登记源文本。
	 *
	 * @param type 翻译键的类型前缀
	 * @param id 对象的资源标识，由 [Util.makeDescriptionId] 转换
	 * @param suffix 追加到翻译键末尾的片段
	 * @param localizedName 使用本模组源语言书写的名称
	 * @return 使用生成键的 [MutableComponent]
	 */
	open fun addLang(type: String, id: Identifier, suffix: String, localizedName: String): MutableComponent = addRawLang(Util.makeDescriptionId(type, id) + "." + suffix, localizedName)

	/**
	 * 使用指定的翻译键直接登记源文本。
	 *
	 * @param key 原样写入语言资源的翻译键
	 * @param value 使用本模组源语言书写的文本
	 * @return 使用指定键的 [MutableComponent]
	 */
	open fun addRawLang(key: String, value: String): MutableComponent = addRawLang(key, value, TranslationSource.Kind.DECLARED)

	private fun addRawLang(key: String, value: String, kind: TranslationSource.Kind): MutableComponent {
		if (dataGeneration.get()) {
			addLocalization(TranslationKey(modid, key), sourceText(value, defaultLocalization), kind)
		}
		return Component.translatable(key)
	}

	/** 声明独立于注册条目的本地化文本，并返回使用该翻译键的组件。 */
	open fun addLocalization(key: TranslationKey, configure: Consumer<LocalizationDeclaration.Builder>): MutableComponent {
		if (dataGeneration.get()) {
			val builder = LocalizationDeclaration.Builder()
			configure.accept(builder)
			addLocalization(key, builder.build(defaultLocalization), TranslationSource.Kind.DECLARED)
		}
		return Component.translatable(key.value)
	}

	private fun addLocalization(key: TranslationKey, declaration: LocalizationDeclaration, kind: TranslationSource.Kind) {
		val translation = RawTranslation(
			key,
			declaration,
			TranslationSource(kind, "[" + modid + "] manual declaration of " + key.value),
		)
		if (provider == null) {
			extraLang.add(translation)
		} else {
			generatedLang.add(translation)
		}
	}

	internal fun collectEntryTranslations(registry: TranslationRegistry, locale: MinecraftLocale) {
		registrationTracker.forEachLocalization { entry, localization ->
			if (localization.kind == TranslationSource.Kind.AUTOMATIC && locale != MinecraftLocale.EN_US) {
				return@forEachLocalization
			}
			val key = TranslationKey(modid, localization.key.get())
			val source = TranslationSource(
				localization.kind,
				"Builder ${entry.id} in ${entry.key.registry()}",
			)
			registry.add(key, locale, localization.declaration.get(), source, terminology())
		}
	}

	private fun getEntryForGenerator(type: GeneratorType<*>, generator: NonNullConsumer<*>): Pair<String, ResourceKey<out Registry<*>>>? {
		for ((key, declaration) in datagensByEntry.column(type)) {
			if (declaration.callback === generator) return key
		}
		return null
	}

	/** 依次执行为指定数据生成器登记的回调。 */
	@Suppress("UNCHECKED_CAST")
	open fun <T : Any> genData(type: GeneratorType<out T>, gen: T) {
		if (!dataGeneration.get()) return
		provider?.putSubProvider(type, gen)
		val debug = DATAGEN_LOGGER.isEnabled(Level.DEBUG, DebugMarkers.DATA)
		for (callback in datagens[type]) {
			val entry = if (debug) getEntryForGenerator(type, callback) else null
			if (debug) {
				val name = getTypeName(type)
				if (entry == null) {
					DATAGEN_LOGGER.debug(DebugMarkers.DATA, modMessage("Generating unassociated data of type {} ({})"), modid, name, type)
				} else {
					DATAGEN_LOGGER.debug(DebugMarkers.DATA, modMessage("Generating data of type {} for entry {} [{}]"), modid, name, entry.left, entry.right.identifier())
				}
			}
			try {
				(callback as NonNullConsumer<T>).accept(gen)
			} catch (error: Exception) {
				val key = if (debug) entry else getEntryForGenerator(type, callback)
				val message = "[$modid] Data generator ${getTypeName(type)} failed" +
					if (key == null) " ($type)" else " for ${key.left} [${key.right.identifier()}]"
				if (!skipErrors) throw IllegalStateException(message, error)
				DATAGEN_LOGGER.error(DebugMarkers.DATA, message, error)
			}
		}
	}

	/**
	 * 开发时可跳过执行失败的注册条目和生成回调，便于继续排查其余条目。该设置仅在开发环境生效。
	 *
	 * @param skipErrors 是否跳过注册及生成时的错误
	 * @return 当前实例
	 */
	open fun skipErrors(skipErrors: Boolean): S {
		if (skipErrors && !isDevEnvironment) {
			LOGGER.warn(
				modMessage("Ignoring skipErrors(true) because the runtime is not a development environment"),
				modid,
			)
		} else {
			this.skipErrors = skipErrors
		}
		return self()
	}

	/**
	 * 为后续构建链设置当前条目名称。同名的方块和方块物品等条目可共用这个名称，下次调用会替换它。
	 *
	 * @param name 后续条目默认采用的名称
	 * @return 当前实例
	 */
	open fun `object`(name: String): S {
		this.currentName = name
		return self()
	}

	/**
	 * 为之后创建的物品构建器指定默认创造模式标签页。已有标签页时传入其注册键，通过 Registrate 创建新标签页时使用对应重载。
	 *
	 * @param creativeModeTab 默认创造模式标签页的注册键
	 * @return 当前实例
	 */
	open fun defaultCreativeTab(creativeModeTab: ResourceKey<CreativeModeTab>): S {
		defaultCreativeModeTab = creativeModeTab
		return self()
	}

	/**
	 * 为指定创造模式标签页追加内容修改回调。每次 [BuildCreativeModeTabContentsEvent] 填充该页时，依次执行已登记的回调。
	 *
	 * @param creativeModeTab 要修改的标签页
	 * @param modifier 用来修改显示内容的回调
	 * @return 当前实例
	 */
	open fun modifyCreativeModeTab(creativeModeTab: ResourceKey<CreativeModeTab>, modifier: Consumer<CreativeModeTabModifier>): S {
		creativeModeTabModifiers.put(creativeModeTab, modifier)
		return self()
	}

	/**
	 * 在链式调用中应用接收当前 [AbstractRegistrate] 实例的扩展函数。
	 *
	 * ```java
	 * public static final RegistryEntry<Block, MyBlock> MY_BLOCK = REGISTRATE.object("my_block")
	 *     .transform(Utils::createMyBlock)
	 *     .get(Registries.BLOCK);
	 * ```
	 *
	 * @param func 接收并返回当前实例类型的转换函数
	 * @return 转换函数的返回值
	 */
	open fun transform(func: NonNullUnaryOperator<S>): S = func.apply(self())

	/**
	 * 在链式调用中执行生成构建器的扩展函数。返回的构建器仍可继续配置。
	 *
	 * ```java
	 * public static final RegistryEntry<Block, MyBlock> MY_BLOCK = REGISTRATE.object("my_block")
	 *     .transform(Utils::createMyBlock)
	 *     .lang("My Block") // 继续配置构建器
	 *     .register();
	 * ```
	 *
	 * @param R 注册表中的基础类型
	 * @param T 条目类型
	 * @param P 父级对象类型
	 * @param S2 返回的构建器类型
	 * @param func 接收当前实例并创建构建器的函数
	 * @return 转换函数创建的 [Builder]
	 */
	open fun <R : Any, T : R, P : Any, S2 : Builder<R, T, P, S2>> transform(func: NonNullFunction<S, S2>): S2 = func.apply(self())

	/**
	 * 以当前条目名称创建构建器，供[自定义构建器](https://github.com/ModularMCLib/RegistrateLib/wiki/Custom-Builders)接入。
	 * 工厂的首个参数是最近一次由 [object] 设置的名称。
	 *
	 * @param R 注册表中的基础类型
	 * @param T 条目类型
	 * @param P 父级对象类型
	 * @param S2 返回的构建器类型
	 * @param factory 接收条目名称和注册回调的工厂
	 * @return 工厂创建的 [Builder]
	 */
	open fun <R : Any, T : R, P : Any, S2 : Builder<R, T, P, S2>> entry(factory: NonNullBiFunction<String, BuilderCallback, S2>) = entry(
		currentName(),
	) { callback: BuilderCallback -> factory.apply(currentName(), callback) }

	/**
	 * 调用工厂创建可提交到当前实例的[自定义构建器](https://github.com/ModularMCLib/RegistrateLib/wiki/Custom-Builders)。
	 *
	 * @param R 注册表中的基础类型
	 * @param T 条目类型
	 * @param P 父级对象类型
	 * @param S2 返回的构建器类型
	 * @param name 本次创建所用的条目名称
	 * @param factory 接收注册回调的构建器工厂
	 * @return 工厂创建的 [Builder]
	 */
	open fun <R : Any, T : R, P : Any, S2 : Builder<R, T, P, S2>> entry(name: String, factory: NonNullFunction<BuilderCallback, S2>): S2 = factory.apply(callback)

	/**
	 * 接收配置完成的构建器，并将其条目加入待注册队列。构建器通常在创建时取得指向此入口的 [BuilderCallback]。
	 *
	 * @param R 注册表中的基础类型
	 * @param T 条目类型
	 * @param name 要创建的条目名称
	 * @param type 要向其中登记条目的注册表键
	 * @param builder 提供条目声明的构建器
	 * @param creator 注册时创建条目对象的工厂
	 * @param entryFactory 可选的 [RegistryEntry] 工厂，用于返回专用条目类型
	 * @return 注册完成后可取得对象的 [RegistryEntry]
	 */
	protected open fun <R : Any, T : R> accept(
		name: String,
		type: ResourceKey<out Registry<R>>,
		builder: Builder<R, T, *, *>,
		creator: NonNullSupplier<out T>,
		entryFactory: NonNullFunction<DeferredHolder<R, T>, out RegistryEntry<R, T>>,
	): RegistryEntry<R, T> {
		val generators = datagensByEntry.row(Pair.of(name, type))
		var localization: EntryLocalization? = null
		var declarations: Collection<EntryMetadata.DataGenerator<*>> = emptyList()
		if (builder is AbstractBuilder<*, *, *, *> && name == builder.name && type == builder.registryKey) {
			declarations = builder.dataGenerators()
			localization = builder.localization()
		}
		val entry = registrationTracker.track(
			name,
			type,
			creator,
			entryFactory,
			EntryMetadata(generators.values, localization),
		)
		for (generator in declarations) {
			if (!generators.containsKey(generator.type)) {
				setDataGenerator(name, type, generator)
			}
		}
		return entry
	}

	/**
	 * 读取本实例为已提交条目保存的声明。
	 */
	open fun getEntryMetadata(key: ResourceKey<*>): EntryMetadata = registrationTracker.getMetadata(key)

	/**
	 * 为自定义对象创建注册表。返回的 [ResourceKey] 可立即用于 [simple]、[generic] 或自定义 [Builder]。
	 * 注册表本身在 [NewRegistryEvent] 触发时创建。
	 *
	 * @param R 新注册表收录的对象类型
	 * @param name 注册表标识的路径部分
	 * @param builder 接收注册表键并创建 [RegistryBuilder] 的函数
	 * @return 待创建注册表的 [ResourceKey]
	 */
	open fun <R : Any> makeRegistry(name: String, builder: Function<ResourceKey<Registry<R>>, RegistryBuilder<R>>): ResourceKey<Registry<R>> {
		val registryId = ResourceKey.createRegistryKey<R>(Identifier.fromNamespaceAndPath(modid, name))
		addModListener(
			this,
			NewRegistryEvent::class.java,
			Consumer { e: NewRegistryEvent -> e.register(builder.apply(registryId).create()) },
		)
		return registryId
	}

	/**
	 * 注册一个仅在服务端加载的数据包注册表，客户端连接时可省略该注册表。
	 * 模组命名空间下的注册表从 `data/<数据包命名空间>/<模组标识>/<注册表路径>/` 读取数据。
	 *
	 * @param name 注册表标识的路径部分
	 * @param codec 服务端读取数据包条目的编解码器
	 * @return 新注册表的 [ResourceKey]
	 * @see makeDatapackRegistry
	 */
	open fun <R : Any> makeDatapackRegistry(name: String, codec: Codec<R>) = makeDatapackRegistry(name, codec, null)

	/**
	 * 注册数据包注册表。模组命名空间下的注册表从 `data/<数据包命名空间>/<模组标识>/<注册表路径>/` 读取数据。
	 * 提供网络编解码器时，服务器会同步条目，客户端也须拥有该注册表，可通过 [net.minecraft.client.multiplayer.ClientPacketListener.registryAccess] 访问。
	 * 网络编解码器为空时由服务端独立加载。
	 *
	 * @param name 注册表标识的路径部分
	 * @param codec 服务端读取数据包条目的编解码器
	 * @param networkCodec 同步到客户端时使用的编解码器，可以为空
	 * @return 新注册表的 [ResourceKey]
	 */
	open fun <R : Any> makeDatapackRegistry(name: String, codec: Codec<R>, networkCodec: Codec<R>?): ResourceKey<Registry<R>> {
		val registryId = ResourceKey.createRegistryKey<R>(Identifier.fromNamespaceAndPath(modid, name))
		addModListener(
			this,
			NewRegistry::class.java,
			Consumer { event: NewRegistry -> event.dataPackRegistry(registryId, codec, networkCodec) },
		)
		return registryId
	}

	/* === 条目构建入口 === */
	// 通用条目

	/** 使用当前条目名称提交注册，创建游戏对象时调用 [factory]。 */
	open fun <R : Any, T : R> simple(registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): RegistryEntry<R, T> = simple(currentName(), registryType, factory)

	/** 以 [name] 提交指定注册表的条目。返回的引用在注册事件绑定后可取得对象。 */
	open fun <R : Any, T : R> simple(name: String, registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>) = simple(this, name, registryType, factory)

	/** 使用当前条目名称提交注册，将 [parent] 作为通用构建器的返回目标。 */
	open fun <R : Any, T : R, P : Any> simple(parent: P, registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): RegistryEntry<R, T> =
		simple(parent, currentName(), registryType, factory)

	/**
	 * 创建通用构建器并立即提交注册。对象由注册事件调用工厂创建。
	 * @param parent 构建链返回的上一级对象
	 * @param name 当前模组命名空间下的条目名称
	 * @param registryType 目标注册表的键
	 * @param factory 注册时创建条目对象的工厂
	 * @return 延迟绑定的条目引用
	 */
	open fun <R : Any, T : R, P : Any> simple(parent: P, name: String, registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): RegistryEntry<R, T> =
		generic(parent, name, registryType, factory).register()

	/** 使用当前名称创建通用构建器，可继续附加标签和数据生成配置。 */
	open fun <R : Any, T : R> generic(registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): NoConfigBuilder<R, T, S> = generic(self(), registryType, factory)

	/** 以 [name] 创建通用构建器。注册时执行 [factory]。 */
	open fun <R : Any, T : R> generic(name: String, registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): NoConfigBuilder<R, T, S> = generic(self(), name, registryType, factory)

	/** 为当前名称创建通用构建器，调用其 `build` 后返回 [parent]。 */
	open fun <R : Any, T : R, P : Any> generic(parent: P, registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): NoConfigBuilder<R, T, P> =
		generic(parent, currentName(), registryType, factory)

	/**
	 * 为任意注册表创建通用条目构建器，供没有专用构建器的条目使用。
	 * @param parent 构建链返回的上一级对象
	 * @param name 当前模组命名空间下的条目名称
	 * @param registryType 目标注册表的键
	 * @param factory 注册时创建条目对象的工厂
	 * @return 可继续设置标签、本地化和数据生成回调的构建器
	 */
	open fun <R : Any, T : R, P : Any> generic(parent: P, name: String, registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): NoConfigBuilder<R, T, P> =
		entry<R, T, P, NoConfigBuilder<R, T, P>>(
			name,
		) { callback: BuilderCallback ->
			NoConfigBuilder(
				this,
				parent,
				name,
				callback,
				registryType,
				factory,
			)
		}

	// 物品

	/** 使用当前条目名称创建物品构建器，由 [factory] 接收配置完成的物品属性。 */
	open fun <T : Item> item(factory: NonNullFunction<Item.Properties, T>): ItemBuilder<T, S> = item(self(), factory)

	/** 以 [name] 创建物品构建器，由 [factory] 接收配置完成的物品属性。 */
	open fun <T : Item> item(name: String, factory: NonNullFunction<Item.Properties, T>): ItemBuilder<T, S> = item(self(), name, factory)

	/** 使用当前条目名称创建物品构建器，调用其 `build` 后返回 [parent]。 */
	open fun <T : Item, P : Any> item(parent: P, factory: NonNullFunction<Item.Properties, T>): ItemBuilder<T, P> = item(parent, currentName(), factory)

	/**
	 * 创建指定名称的物品构建器。构建器应用属性变换后，才将结果传给 [factory]。
	 * @param parent 构建链返回的上一级对象
	 * @param name 当前模组命名空间下的物品名称
	 * @param factory 使用最终物品属性创建物品的工厂
	 * @return 可继续配置物品属性和数据生成的构建器
	 */
	open fun <T : Item, P : Any> item(parent: P, name: String, factory: NonNullFunction<Item.Properties, T>): ItemBuilder<T, P> = entry<Item, T, P, ItemBuilder<T, P>>(
		name,
	) { callback: BuilderCallback ->
		ItemBuilder.create(this, parent, name, callback, factory)
			.tab(defaultCreativeModeTab)
	}

	// 方块

	/** 使用当前条目名称创建方块构建器，由 [factory] 接收配置完成的方块属性。 */
	open fun <T : Block> block(factory: NonNullFunction<BlockBehaviour.Properties, T>): BlockBuilder<T, S> = block(self(), factory)

	/** 以 [name] 创建方块构建器，由 [factory] 接收配置完成的方块属性。 */
	open fun <T : Block> block(name: String, factory: NonNullFunction<BlockBehaviour.Properties, T>): BlockBuilder<T, S> = block(self(), name, factory)

	/** 使用当前条目名称创建方块构建器，调用其 `build` 后返回 [parent]。 */
	open fun <T : Block, P : Any> block(parent: P, factory: NonNullFunction<BlockBehaviour.Properties, T>): BlockBuilder<T, P> = block(parent, currentName(), factory)

	/**
	 * 创建指定名称的方块构建器。构建器应用属性变换后，才将结果传给 [factory]。
	 * @param parent 构建链返回的上一级对象
	 * @param name 当前模组命名空间下的方块名称
	 * @param factory 使用最终方块属性创建方块的工厂
	 * @return 可继续配置方块属性、附属物品和数据生成的构建器
	 */
	open fun <T : Block, P : Any> block(parent: P, name: String, factory: NonNullFunction<BlockBehaviour.Properties, T>): BlockBuilder<T, P> = entry<Block, T, P, BlockBuilder<T, P>>(
		name,
	) { callback: BuilderCallback ->
		BlockBuilder.create(
			this,
			parent,
			name,
			callback,
			factory,
		)
	}

	// 实体

	/** 使用当前条目名称创建实体类型构建器，以 [classification] 设置原版实体分类。 */
	open fun <T : Entity> entity(factory: EntityFactory<T>, classification: MobCategory): EntityBuilder<T, S> = entity(self(), factory, classification)

	/** 以 [name] 创建实体类型构建器，以 [classification] 设置原版实体分类。 */
	open fun <T : Entity> entity(name: String, factory: EntityFactory<T>, classification: MobCategory): EntityBuilder<T, S> = entity(self(), name, factory, classification)

	/** 使用当前条目名称创建实体类型构建器，调用其 `build` 后返回 [parent]。 */
	open fun <T : Entity, P : Any> entity(parent: P, factory: EntityFactory<T>, classification: MobCategory): EntityBuilder<T, P> = entity(parent, currentName(), factory, classification)

	/**
	 * 创建指定名称的实体类型构建器。游戏创建实体实例时调用 [factory]。
	 * @param parent 构建链返回的上一级对象
	 * @param name 当前模组命名空间下的实体类型名称
	 * @param factory 由实体类型和所在世界创建实体实例的工厂
	 * @param classification 原版使用的实体分类
	 * @return 可继续配置实体类型、渲染器和生成数据的构建器
	 */
	open fun <T : Entity, P : Any> entity(parent: P, name: String, factory: EntityFactory<T>, classification: MobCategory): EntityBuilder<T, P> =
		entry<EntityType<*>, EntityType<T>, P, EntityBuilder<T, P>>(
			name,
		) { callback: BuilderCallback ->
			EntityBuilder.create(
				this,
				parent,
				name,
				callback,
				factory,
				classification,
			)
		}

	// 方块实体

	/** 使用当前条目名称创建方块实体类型构建器，可接着指定适用方块。 */
	open fun <T : BlockEntity> blockEntity(factory: BlockEntityFactory<T>): BlockEntityBuilder<T, S> = blockEntity(self(), factory)

	/** 以 [name] 创建方块实体类型构建器，可接着指定适用方块。 */
	open fun <T : BlockEntity> blockEntity(name: String, factory: BlockEntityFactory<T>): BlockEntityBuilder<T, S> = blockEntity(self(), name, factory)

	/** 使用当前条目名称创建方块实体类型构建器，调用其 `build` 后返回 [parent]。 */
	open fun <T : BlockEntity, P : Any> blockEntity(parent: P, factory: BlockEntityFactory<T>): BlockEntityBuilder<T, P> = blockEntity(parent, currentName(), factory)

	/**
	 * 创建指定名称的方块实体类型构建器，由 [factory] 为指定位置创建方块实体实例。
	 * @param parent 构建链返回的上一级对象
	 * @param name 当前模组命名空间下的方块实体类型名称
	 * @param factory 使用类型、位置和方块状态创建实例的工厂
	 * @return 可继续指定适用方块和渲染器的构建器
	 */
	open fun <T : BlockEntity, P : Any> blockEntity(parent: P, name: String, factory: BlockEntityFactory<T>): BlockEntityBuilder<T, P> =
		entry<BlockEntityType<*>, BlockEntityType<T>, P, BlockEntityBuilder<T, P>>(
			name,
		) { callback: BuilderCallback ->
			BlockEntityBuilder.create(
				this,
				parent,
				name,
				callback,
				factory,
			)
		}

	// 流体

	/** 为当前名称创建流体构建器，使用默认纹理、默认流体类型和默认的流动流体实现。 */
	open fun fluid(): FluidBuilder<Flowing, S> = fluid(self())

	/** 为当前名称创建流体构建器，使用默认纹理和默认的流动流体实现，并由 [typeFactory] 创建流体类型。 */
	open fun fluid(typeFactory: FluidTypeFactory): FluidBuilder<Flowing, S> = fluid(self(), typeFactory)

	/** 为当前名称创建流体构建器，使用默认纹理和默认的流动流体实现，并复用 [fluidType] 提供的流体类型。 */
	open fun fluid(fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, S> = fluid(self(), fluidType)

	/** 为当前名称创建流体构建器，使用指定的源流体纹理和流动纹理，并使用默认流体类型和默认的流动流体实现。 */
	open fun fluid(stillTexture: Identifier, flowingTexture: Identifier): FluidBuilder<Flowing, S> = fluid(self(), stillTexture, flowingTexture)

	/** 为当前名称创建流体构建器，使用指定纹理和默认的流动流体实现，并由 [typeFactory] 创建流体类型。 */
	open fun fluid(stillTexture: Identifier, flowingTexture: Identifier, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, S> = fluid(self(), stillTexture, flowingTexture, typeFactory)

	/** 为当前名称创建流体构建器，使用指定纹理和默认的流动流体实现，并复用 [fluidType] 提供的流体类型。 */
	open fun fluid(stillTexture: Identifier, flowingTexture: Identifier, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, S> = fluid(self(), stillTexture, flowingTexture, fluidType)

	/** 为当前名称创建流体构建器，使用指定纹理和默认流体类型，并由 [fluidFactory] 创建流动流体。 */
	open fun <T : BaseFlowingFluid> fluid(stillTexture: Identifier, flowingTexture: Identifier, fluidFactory: FluidFactory<T>): FluidBuilder<T, S> =
		fluid(self(), stillTexture, flowingTexture, fluidFactory)

	/** 为当前名称创建流体构建器，使用指定纹理，并分别由 [typeFactory] 和 [fluidFactory] 创建流体类型和流动流体。 */
	open fun <T : BaseFlowingFluid> fluid(stillTexture: Identifier, flowingTexture: Identifier, typeFactory: FluidTypeFactory, fluidFactory: FluidFactory<T>): FluidBuilder<T, S> =
		fluid(self(), stillTexture, flowingTexture, typeFactory, fluidFactory)

	/** 为当前名称创建流体构建器，使用指定纹理和 [fluidType] 提供的流体类型，并由 [fluidFactory] 创建流动流体。 */
	open fun <T : BaseFlowingFluid> fluid(stillTexture: Identifier, flowingTexture: Identifier, fluidType: NonNullSupplier<FluidType>, fluidFactory: FluidFactory<T>): FluidBuilder<T, S> =
		fluid(self(), stillTexture, flowingTexture, fluidType, fluidFactory)

	/** 以 [name] 为注册名创建流体构建器，使用默认纹理、默认流体类型和默认的流动流体实现。默认纹理路径仍以当前名称为基础，须先调用 [object]。 */
	open fun fluid(name: String): FluidBuilder<Flowing, S> = fluid(self(), name)

	/** 以 [name] 为注册名创建流体构建器，使用默认纹理和默认的流动流体实现，并由 [typeFactory] 创建流体类型。默认纹理路径仍以当前名称为基础，须先调用 [object]。 */
	open fun fluid(name: String, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, S> = fluid(self(), name, typeFactory)

	/** 以 [name] 为注册名创建流体构建器，使用默认纹理和默认的流动流体实现，并复用 [fluidType] 提供的流体类型。默认纹理路径仍以当前名称为基础，须先调用 [object]。 */
	open fun fluid(name: String, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, S> = fluid(self(), name, fluidType)

	/** 以 [name] 为注册名创建流体构建器，使用指定的源流体纹理和流动纹理，并使用默认流体类型和默认的流动流体实现。 */
	open fun fluid(name: String, stillTexture: Identifier, flowingTexture: Identifier): FluidBuilder<Flowing, S> = fluid(self(), name, stillTexture, flowingTexture)

	/** 以 [name] 为注册名创建流体构建器，使用指定纹理和默认的流动流体实现，并由 [typeFactory] 创建流体类型。 */
	open fun fluid(name: String, stillTexture: Identifier, flowingTexture: Identifier, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, S> =
		fluid(self(), name, stillTexture, flowingTexture, typeFactory)

	/** 以 [name] 为注册名创建流体构建器，使用指定纹理和默认的流动流体实现，并复用 [fluidType] 提供的流体类型。 */
	open fun fluid(name: String, stillTexture: Identifier, flowingTexture: Identifier, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, S> =
		fluid(self(), name, stillTexture, flowingTexture, fluidType)

	/** 以 [name] 为注册名创建流体构建器，使用指定纹理和默认流体类型，并由 [fluidFactory] 创建流动流体。 */
	open fun <T : BaseFlowingFluid> fluid(name: String, stillTexture: Identifier, flowingTexture: Identifier, fluidFactory: FluidFactory<T>): FluidBuilder<T, S> =
		fluid(self(), name, stillTexture, flowingTexture, fluidFactory)

	/** 以 [name] 为注册名创建流体构建器，使用指定纹理，并分别由 [typeFactory] 和 [fluidFactory] 创建流体类型和流动流体。 */
	open fun <T : BaseFlowingFluid> fluid(name: String, stillTexture: Identifier, flowingTexture: Identifier, typeFactory: FluidTypeFactory, fluidFactory: FluidFactory<T>): FluidBuilder<T, S> =
		fluid(self(), name, stillTexture, flowingTexture, typeFactory, fluidFactory)

	/** 以 [name] 为注册名创建流体构建器，使用指定纹理和 [fluidType] 提供的流体类型，并由 [fluidFactory] 创建流动流体。 */
	open fun <T : BaseFlowingFluid> fluid(
		name: String,
		stillTexture: Identifier,
		flowingTexture: Identifier,
		fluidType: NonNullSupplier<FluidType>,
		fluidFactory: FluidFactory<T>,
	): FluidBuilder<T, S> = fluid(self(), name, stillTexture, flowingTexture, fluidType, fluidFactory)

	/** 为当前名称创建流体构建器，使用默认纹理、默认流体类型和默认的流动流体实现。调用构建器的 `build` 后返回 [parent]。 */
	open fun <P : Any> fluid(parent: P): FluidBuilder<Flowing, P> = fluid(parent, currentName())

	/** 为当前名称创建流体构建器，使用默认纹理和默认的流动流体实现，并由 [typeFactory] 创建流体类型。调用构建器的 `build` 后返回 [parent]。 */
	open fun <P : Any> fluid(parent: P, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, P> = fluid(parent, currentName(), typeFactory)

	/** 为当前名称创建流体构建器，使用默认纹理和默认的流动流体实现，并复用 [fluidType] 提供的流体类型。调用构建器的 `build` 后返回 [parent]。 */
	open fun <P : Any> fluid(parent: P, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, P> = fluid(parent, currentName(), fluidType)

	/** 为当前名称创建流体构建器，使用指定的源流体纹理和流动纹理，并使用默认流体类型和默认的流动流体实现。调用构建器的 `build` 后返回 [parent]。 */
	open fun <P : Any> fluid(parent: P, stillTexture: Identifier, flowingTexture: Identifier): FluidBuilder<Flowing, P> = fluid(parent, currentName(), stillTexture, flowingTexture)

	/** 为当前名称创建流体构建器，使用指定纹理和默认的流动流体实现，并由 [typeFactory] 创建流体类型。调用构建器的 `build` 后返回 [parent]。 */
	open fun <P : Any> fluid(parent: P, stillTexture: Identifier, flowingTexture: Identifier, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, P> =
		fluid(parent, currentName(), stillTexture, flowingTexture, typeFactory)

	/** 为当前名称创建流体构建器，使用指定纹理和默认的流动流体实现，并复用 [fluidType] 提供的流体类型。调用构建器的 `build` 后返回 [parent]。 */
	open fun <P : Any> fluid(parent: P, stillTexture: Identifier, flowingTexture: Identifier, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, P> =
		fluid(parent, currentName(), stillTexture, flowingTexture, fluidType)

	/** 为当前名称创建流体构建器，使用指定纹理和默认流体类型，并由 [fluidFactory] 创建流动流体。调用构建器的 `build` 后返回 [parent]。 */
	open fun <T : BaseFlowingFluid, P : Any> fluid(parent: P, stillTexture: Identifier, flowingTexture: Identifier, fluidFactory: FluidFactory<T>): FluidBuilder<T, P> =
		fluid(parent, currentName(), stillTexture, flowingTexture, fluidFactory)

	/** 为当前名称创建流体构建器，使用指定纹理，并分别由 [typeFactory] 和 [fluidFactory] 创建流体类型和流动流体。调用构建器的 `build` 后返回 [parent]。 */
	open fun <T : BaseFlowingFluid, P : Any> fluid(parent: P, stillTexture: Identifier, flowingTexture: Identifier, typeFactory: FluidTypeFactory, fluidFactory: FluidFactory<T>): FluidBuilder<T, P> =
		fluid(parent, currentName(), stillTexture, flowingTexture, typeFactory, fluidFactory)

	/** 为当前名称创建流体构建器，使用指定纹理和 [fluidType] 提供的流体类型，并由 [fluidFactory] 创建流动流体。调用构建器的 `build` 后返回 [parent]。 */
	open fun <T : BaseFlowingFluid, P : Any> fluid(
		parent: P,
		stillTexture: Identifier,
		flowingTexture: Identifier,
		fluidType: NonNullSupplier<FluidType>,
		fluidFactory: FluidFactory<T>,
	): FluidBuilder<T, P> = fluid(parent, currentName(), stillTexture, flowingTexture, fluidType, fluidFactory)

	/** 以 [name] 为注册名创建流体构建器，使用默认纹理、默认流体类型和默认的流动流体实现。调用构建器的 `build` 后返回 [parent]。默认纹理路径仍以当前名称为基础，须先调用 [object]。 */
	open fun <P : Any> fluid(parent: P, name: String): FluidBuilder<Flowing, P> = fluid(parent, name, defaultStillTexture(), defaultFlowingTexture())

	/** 以 [name] 为注册名创建流体构建器，使用默认纹理和默认的流动流体实现，并由 [typeFactory] 创建流体类型。调用构建器的 `build` 后返回 [parent]。默认纹理路径仍以当前名称为基础，须先调用 [object]。 */
	open fun <P : Any> fluid(parent: P, name: String, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, P> = fluid(parent, name, defaultStillTexture(), defaultFlowingTexture(), typeFactory)

	/** 以 [name] 为注册名创建流体构建器，使用默认纹理和默认的流动流体实现，并复用 [fluidType] 提供的流体类型。调用构建器的 `build` 后返回 [parent]。默认纹理路径仍以当前名称为基础，须先调用 [object]。 */
	open fun <P : Any> fluid(parent: P, name: String, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, P> = fluid(parent, name, defaultStillTexture(), defaultFlowingTexture(), fluidType)

	private fun defaultStillTexture(): Identifier = Identifier.fromNamespaceAndPath(modid, "block/" + currentName() + "_still")

	private fun defaultFlowingTexture(): Identifier = Identifier.fromNamespaceAndPath(modid, "block/" + currentName() + "_flow")

	/** 以 [name] 为注册名创建流体构建器，使用指定的源流体纹理和流动纹理，并使用默认流体类型和默认的流动流体实现。调用构建器的 `build` 后返回 [parent]。 */
	open fun <P : Any> fluid(parent: P, name: String, stillTexture: Identifier, flowingTexture: Identifier): FluidBuilder<Flowing, P> = entry<Fluid, Flowing, P, FluidBuilder<Flowing, P>>(
		name,
	) { callback: BuilderCallback ->
		FluidBuilder.create(
			this,
			parent,
			name,
			callback,
			FluidTypeFactory(::FluidType),
		)
	}.model(stillTexture, flowingTexture)

	/** 以 [name] 为注册名创建流体构建器，使用指定纹理和默认的流动流体实现，并由 [typeFactory] 创建流体类型。调用构建器的 `build` 后返回 [parent]。 */
	open fun <P : Any> fluid(parent: P, name: String, stillTexture: Identifier, flowingTexture: Identifier, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, P> =
		entry<Fluid, Flowing, P, FluidBuilder<Flowing, P>>(
			name,
		) { callback: BuilderCallback ->
			FluidBuilder.create(
				this,
				parent,
				name,
				callback,
				typeFactory,
			)
		}.model(stillTexture, flowingTexture)

	/** 以 [name] 为注册名创建流体构建器，使用指定纹理和默认的流动流体实现，并复用 [fluidType] 提供的流体类型。调用构建器的 `build` 后返回 [parent]。 */
	open fun <P : Any> fluid(parent: P, name: String, stillTexture: Identifier, flowingTexture: Identifier, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, P> =
		entry<Fluid, Flowing, P, FluidBuilder<Flowing, P>>(
			name,
		) { callback: BuilderCallback ->
			FluidBuilder.create(
				this,
				parent,
				name,
				callback,
				fluidType,
			)
		}.model(stillTexture, flowingTexture)

	/** 以 [name] 为注册名创建流体构建器，使用指定纹理和默认流体类型，并由 [fluidFactory] 创建流动流体。调用构建器的 `build` 后返回 [parent]。 */
	open fun <T : BaseFlowingFluid, P : Any> fluid(parent: P, name: String, stillTexture: Identifier, flowingTexture: Identifier, fluidFactory: FluidFactory<T>): FluidBuilder<T, P> =
		entry<Fluid, T, P, FluidBuilder<T, P>>(
			name,
		) { callback: BuilderCallback ->
			FluidBuilder.create(
				this,
				parent,
				name,
				callback,
				fluidFactory,
			)
		}.model(stillTexture, flowingTexture)

	/** 以 [name] 为注册名创建流体构建器，使用指定纹理，并分别由 [typeFactory] 和 [fluidFactory] 创建流体类型和流动流体。调用构建器的 `build` 后返回 [parent]。 */
	open fun <T : BaseFlowingFluid, P : Any> fluid(
		parent: P,
		name: String,
		stillTexture: Identifier,
		flowingTexture: Identifier,
		typeFactory: FluidTypeFactory,
		fluidFactory: FluidFactory<T>,
	): FluidBuilder<T, P> = entry<Fluid, T, P, FluidBuilder<T, P>>(
		name,
	) { callback: BuilderCallback ->
		FluidBuilder.create(
			this,
			parent,
			name,
			callback,
			typeFactory,
			fluidFactory,
		)
	}.model(stillTexture, flowingTexture)

	/** 以 [name] 为注册名创建流体构建器，使用指定纹理和 [fluidType] 提供的流体类型，并由 [fluidFactory] 创建流动流体。调用构建器的 `build` 后返回 [parent]。 */
	open fun <T : BaseFlowingFluid, P : Any> fluid(
		parent: P,
		name: String,
		stillTexture: Identifier,
		flowingTexture: Identifier,
		fluidType: NonNullSupplier<FluidType>,
		fluidFactory: FluidFactory<T>,
	): FluidBuilder<T, P> = entry<Fluid, T, P, FluidBuilder<T, P>>(
		name,
	) { callback: BuilderCallback ->
		FluidBuilder.create(
			this,
			parent,
			name,
			callback,
			fluidType,
			fluidFactory,
		)
	}.model(stillTexture, flowingTexture)

	// 菜单

	/** 使用当前条目名称创建普通菜单构建器。界面工厂仅在客户端取得。 */
	open fun <T : AbstractContainerMenu, SC> menu(factory: MenuFactory<T>, screenFactory: NonNullSupplier<ScreenFactory<T, SC>>) where SC : Screen, SC : MenuAccess<T> =
		menu(currentName(), factory, screenFactory)

	/** 以 [name] 创建普通菜单构建器。界面工厂仅在客户端取得。 */
	open fun <T : AbstractContainerMenu, SC> menu(name: String, factory: MenuFactory<T>, screenFactory: NonNullSupplier<ScreenFactory<T, SC>>) where SC : Screen, SC : MenuAccess<T> =
		menu(self(), name, factory, screenFactory)

	/** 使用当前条目名称创建普通菜单构建器，调用其 `build` 后返回 [parent]。 */
	open fun <T : AbstractContainerMenu, SC, P : Any> menu(parent: P, factory: MenuFactory<T>, screenFactory: NonNullSupplier<ScreenFactory<T, SC>>) where SC : Screen, SC : MenuAccess<T> =
		menu(parent, currentName(), factory, screenFactory)

	/**
	 * 创建普通菜单构建器。菜单工厂接收菜单类型、窗口标识和玩家物品栏。
	 * @param parent 构建链返回的上一级对象
	 * @param name 当前模组命名空间下的菜单类型名称
	 * @param factory 菜单实例工厂
	 * @param screenFactory 客户端的界面工厂取值器
	 * @return 尚可配置注册回调的菜单构建器
	 */
	open fun <T : AbstractContainerMenu, SC, P : Any> menu(
		parent: P,
		name: String,
		factory: MenuFactory<T>,
		screenFactory: NonNullSupplier<ScreenFactory<T, SC>>,
	): MenuBuilder<T, SC, P> where SC : Screen, SC : MenuAccess<T> = entry<MenuType<*>, MenuType<T>, P, MenuBuilder<T, SC, P>>(
		name,
	) { callback: BuilderCallback ->
		MenuBuilder(
			this,
			parent,
			name,
			callback,
			factory,
			screenFactory,
		)
	}

	/** 使用当前条目名称创建支持读取网络数据的菜单构建器。 */
	open fun <T : AbstractContainerMenu, SC> menu(factory: ForgeMenuFactory<T>, screenFactory: NonNullSupplier<ScreenFactory<T, SC>>) where SC : Screen, SC : MenuAccess<T> =
		menu(currentName(), factory, screenFactory)

	/** 以 [name] 创建支持读取网络数据的菜单构建器。 */
	open fun <T : AbstractContainerMenu, SC> menu(name: String, factory: ForgeMenuFactory<T>, screenFactory: NonNullSupplier<ScreenFactory<T, SC>>) where SC : Screen, SC : MenuAccess<T> =
		menu(self(), name, factory, screenFactory)

	/** 使用当前条目名称创建支持读取网络数据的菜单构建器，调用其 `build` 后返回 [parent]。 */
	open fun <T : AbstractContainerMenu, SC, P : Any> menu(parent: P, factory: ForgeMenuFactory<T>, screenFactory: NonNullSupplier<ScreenFactory<T, SC>>) where SC : Screen, SC : MenuAccess<T> =
		menu(parent, currentName(), factory, screenFactory)

	/**
	 * 创建支持读取网络数据的菜单构建器。打开菜单时，工厂可读取服务端发送的附加数据。
	 * @param parent 构建链返回的上一级对象
	 * @param name 当前模组命名空间下的菜单类型名称
	 * @param factory 可读取打开数据的菜单实例工厂
	 * @param screenFactory 客户端的界面工厂取值器
	 * @return 尚可配置注册回调的菜单构建器
	 */
	open fun <T : AbstractContainerMenu, SC, P : Any> menu(
		parent: P,
		name: String,
		factory: ForgeMenuFactory<T>,
		screenFactory: NonNullSupplier<ScreenFactory<T, SC>>,
	): MenuBuilder<T, SC, P> where SC : Screen, SC : MenuAccess<T> = entry<MenuType<*>, MenuType<T>, P, MenuBuilder<T, SC, P>>(
		name,
	) { callback: BuilderCallback ->
		MenuBuilder(
			this,
			parent,
			name,
			callback,
			factory,
			screenFactory,
		)
	}

	// 创造模式标签页

	/** 使用当前条目名称创建创造模式标签页构建器，并将该标签页设为本实例的默认页。 */
	open fun defaultCreativeTab(): NoConfigBuilder<CreativeModeTab, CreativeModeTab, S> = defaultCreativeTab(self())

	/** 以 [name] 创建创造模式标签页构建器，并将该标签页设为本实例的默认页。 */
	open fun defaultCreativeTab(name: String): NoConfigBuilder<CreativeModeTab, CreativeModeTab, S> = defaultCreativeTab(self(), name)

	/** 使用当前名称创建默认标签页，调用构建器的 `build` 后返回 [parent]。 */
	open fun <P : Any> defaultCreativeTab(parent: P): NoConfigBuilder<CreativeModeTab, CreativeModeTab, P> = defaultCreativeTab(parent, currentName())

	/** 以 [name] 创建默认标签页，调用构建器的 `build` 后返回 [parent]。 */
	open fun <P : Any> defaultCreativeTab(parent: P, name: String): NoConfigBuilder<CreativeModeTab, CreativeModeTab, P> = defaultCreativeTab(
		parent,
		name,
	) { tab: CreativeModeTab.Builder ->
	}

	/** 为当前名称构建默认标签页，由 [config] 调整原版标签页构建器。 */
	open fun defaultCreativeTab(config: Consumer<CreativeModeTab.Builder>): NoConfigBuilder<CreativeModeTab, CreativeModeTab, S> = defaultCreativeTab(self(), config)

	/** 以 [name] 构建默认标签页，由 [config] 调整原版标签页构建器。 */
	open fun defaultCreativeTab(name: String, config: Consumer<CreativeModeTab.Builder>): NoConfigBuilder<CreativeModeTab, CreativeModeTab, S> = defaultCreativeTab(self(), name, config)

	/** 为当前名称构建默认标签页，由 [config] 调整原版构建器，完成后返回 [parent]。 */
	open fun <P : Any> defaultCreativeTab(parent: P, config: Consumer<CreativeModeTab.Builder>): NoConfigBuilder<CreativeModeTab, CreativeModeTab, P> =
		defaultCreativeTab(parent, currentName(), config)

	/**
	 * 创建标签页构建器并立即更新本实例的默认标签页指向。实际注册时先设置默认标题和图标，再执行 [config]。
	 * @param parent 构建链返回的上一级对象
	 * @param name 当前模组命名空间下的标签页名称
	 * @param config 用于调整标题、图标或展示内容的回调
	 * @return 可追加注册回调的标签页构建器
	 */
	open fun <P : Any> defaultCreativeTab(parent: P, name: String, config: Consumer<CreativeModeTab.Builder>): NoConfigBuilder<CreativeModeTab, CreativeModeTab, P> {
		this.defaultCreativeModeTab = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(this.modid, name),
		)
		return this.generic(
			parent,
			name,
			Registries.CREATIVE_MODE_TAB,
		) {
			val builder = CreativeModeTab.builder()
				.icon {
					getAll(Registries.ITEM).firstOrNull()?.get()?.let(::ItemStack) ?: ItemStack.EMPTY
				}
				.title(
					addRawLang(
						Util.makeDescriptionId("itemGroup", this.defaultCreativeModeTab.identifier()),
						toEnglishName(name),
						TranslationSource.Kind.AUTOMATIC,
					),
				)
			config.accept(builder)
			builder.build()
		}
	}

	companion object {
		private val LOGGER = core()
		private val DATAGEN_LOGGER = datagen()

		/** NeoForge 在开发环境中运行时启用开发诊断。 */
		@get:JvmStatic
		val isDevEnvironment: Boolean
			get() = !FMLEnvironment.isProduction()
	}
}
