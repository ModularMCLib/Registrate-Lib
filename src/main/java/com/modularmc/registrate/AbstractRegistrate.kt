// KtLint folds forwarding overloads across the configured line limit during formatting.
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
import com.modularmc.registrate.internal.RegistrationTracker
import com.modularmc.registrate.internal.event.OneTimeEventReceiver.Companion.addModListener
import com.modularmc.registrate.internal.lifecycle.RegistrateLifecycle
import com.modularmc.registrate.internal.util.DebugMarkers
import com.modularmc.registrate.internal.util.RegistrateLogger.core
import com.modularmc.registrate.internal.util.RegistrateLogger.datagen
import com.modularmc.registrate.internal.util.RegistrateLogger.modMessage
import com.modularmc.registrate.localization.EntryLocalization
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.LocalizationDeclaration.Companion.sourceText
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.resolution.TranslationRegistry
import com.modularmc.registrate.localization.terminology.LocalizationCatalog
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

import net.minecraft.Util
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.MenuAccess
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
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
import org.apache.commons.lang3.tuple.Pair
import org.apache.logging.log4j.Level

import java.util.Optional
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.BiConsumer
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * Manages all registrations and data generators for a mod.
 *
 * Generally *not* thread-safe, as it holds the current name of the object being built statefully, and uses
 * non-concurrent collections.
 *
 * Begin a new object via [object]. This name will be used for all future entries until the next
 * invocation of [object]. Alternatively, the methods that accept a name
 * parameter (such as [block]) can be used. These do not affect the current name state.
 *
 * A simple use may look like:
 *
 * ```java
 * public static final Registrate REGISTRATE = Registrate.create("mymod");
 * public static final RegistryEntry<MyBlock> MY_BLOCK = REGISTRATE.object("my_block")
 *     .block(MyBlock::new)
 *     .defaultItem()
 *     .register();
 * ```
 *
 * For specifics as to building different registry entries, read the documentation on their respective builders.
 */
abstract class AbstractRegistrate<S : AbstractRegistrate<S>> protected constructor(
	/**
	 * The mod ID that this [AbstractRegistrate] is creating objects for
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

	open fun doDatagen(): NonNullSupplier<Boolean> = dataGeneration

	/**
	 * Get the mod event bus that event listeners will be registered to. Useful when Registrate is used in mods that use
	 * alternative language loaders, such as forgelin.
	 */

	var modEventBus: IEventBus? = null

	private lateinit var currentName: String
	private var skipErrors = false

	/**
	 * Use this in custom implementations to hide unsafe casts to [S] when returning self.
	 *
	 * @return This [AbstractRegistrate] object, cast to [S]
	 */
	@Suppress("UNCHECKED_CAST")
	protected fun self(): S = this as S

	/**
	 * Called during [creation][Registrate.create] to initialize event listeners. Custom implementations
	 * may add their own event listeners by overriding this.
	 *
	 * *Always* call `super` in your override unless you know what you are doing!
	 *
	 * @param bus
	 * The event bus
	 * @return This [AbstractRegistrate] object
	 */
	open fun registerEventListeners(bus: IEventBus): S {
		lifecycle.register(bus)
		return self()
	}

	/**
	 * Called once per registry to gather collected registrations and add entries to the registry. May be overriden in
	 * custom implementations to perform additional actions upon entry registration, but
	 * *must* call `super`.
	 *
	 * @param event
	 * The [RegisterEvent] being fired, use [RegisterEvent.getRegistryKey] to query the
	 * registry type
	 */
	protected open fun onRegister(event: RegisterEvent) {
		registrationTracker.onRegister(event, skipErrors, isDevEnvironment)
	}

	/**
	 * Called once per registry at the [lowest priority][EventPriority.LOWEST] to perform any actions that must
	 * happen after all other entries have been registered, including from other mods. May
	 * be overriden in custom implementations to perform additional actions upon entry registration, but *must*
	 * call `super`.
	 *
	 * @param event
	 * The [RegisterEvent] being fired, use [RegisterEvent.getRegistryKey] to query the
	 * registry type
	 */
	protected open fun onRegisterLate(event: RegisterEvent) {
		registrationTracker.onRegisterLate(event)
	}

	/**
	 * Called when a [CreativeModeTab] is being populated to fill in any entries that belong there. Can be
	 * overriden in custom implementations.
	 *
	 * @param event
	 * The event
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
	 * Called when datagen begins to add our provider to the generator. Can be overriden in custom implementations.
	 *
	 * @param event
	 * The event
	 */
	protected open fun onData(event: GatherDataEvent) {
		if (event.mods.contains(modid)) {
			val dataProvider = RegistrateDataProvider(this, modid, event)
			provider = dataProvider
			dataProvider.attach(event)
		}
	}

	/**
	 * Get the current name (from the last call to [object]), throwing an exception if it is not set.
	 *
	 * @return The current entry name

	 */
	protected open fun currentName(): String = currentName

	/**
	 * Allows retrieval of a previously created entry, of the current name (from the last invocation of
	 * [object]. Useful to retrieve a different entry than the final state of your
	 * chain may produce, e.g.
	 *
	 * ```java
	 * public static final RegistryEntry<BlockItem> MY_BLOCK_ITEM = REGISTRATE.object("my_block")
	 *     .block(MyBlock::new)
	 *     .defaultItem()
	 *     .lang("My Special Block")
	 *     .build()
	 *     .get(Item.class);
	 * ```
	 *
	 * @param R
	 * The type of the registry for which to retrieve the entry
	 * @param T
	 * The type of the entry to return
	 * @param type
	 * A [ResourceKey] for the registry
	 * @return A [RegistryEntry] which will supply the requested entry, if it exists
	 * @throws IllegalArgumentException
	 * if no such registration has been done
	 * @throws NullPointerException
	 * if current name has not been set via [object]
	 */
	open fun <R : Any, T : R> get(type: ResourceKey<out Registry<R>>): RegistryEntry<R, T> = this.get<R, T>(currentName(), type)

	/**
	 * Allows retrieval of a previously created entry. Useful to retrieve arbitrary entries that may have been created
	 * as side-effects of earlier registrations.
	 *
	 * ```java
	 * public static final RegistryEntry<MyBlock> MY_BLOCK = REGISTRATE.object("my_block")
	 *     .block(MyBlock::new)
	 *     .defaultItem()
	 *     .register();
	 * ... public static final RegistryEntry<BlockItem> MY_BLOCK_ITEM = REGISTRATE.get("my_block", Registries.ITEM);
	 * ```
	 *
	 * @param R
	 * The type of the registry for which to retrieve the entry
	 * @param T
	 * The type of the entry to return
	 * @param name
	 * The name of the registry entry to request
	 * @param type
	 * A [ResourceKey] for the registry
	 * @return A [RegistryEntry] which will supply the requested entry, if it exists
	 * @throws IllegalArgumentException
	 * if no such registration has been done
	 */
	open fun <R : Any, T : R> get(name: String, type: ResourceKey<out Registry<R>>): RegistryEntry<R, T> = registrationTracker.get<R, T>(name, type)

	/**
	 * Allows retrieval of a previously created entry that may or may not exist. Possibly useful in some very specific
	 * scenarios, internally used during fluid registration.
	 *
	 * @param R
	 * The type of the registry for which to retrieve the entry
	 * @param T
	 * The type of the entry to return
	 * @param name
	 * The name of the registry entry to request
	 * @param type
	 * A class representing the registry type
	 * @return A [RegistryEntry] which will supply the requested entry, if it exists
	 */
	open fun <R : Any, T : R> getOptional(name: String, type: ResourceKey<out Registry<R>>): Optional<RegistryEntry<R, T>> = registrationTracker.getOptional<R, T>(name, type)

	/**
	 * Gather a collection of all entries registered for a certain registry
	 *
	 * Note that this can be called before registration is complete, but the [entries][RegistryEntry] will be empty
	 * at that time.
	 *
	 * @param R
	 * Registry type
	 * @param type
	 * A [ResourceKey] for the registry in question
	 * @return A collection of [RegistryEntry] objects representing all entries in the given registry which are
	 * known to this [AbstractRegistrate] object.
	 */
	open fun <R : Any, T : R> getAll(type: ResourceKey<out Registry<R>>): Collection<RegistryEntry<R, T>> = registrationTracker.getAll<R, T>(type)

	/**
	 * Add a callback to be invoked when a certain entry has been registered. This will be invoked *immediately*
	 * following registration, before further entries are registered.
	 *
	 * @param R
	 * Registry type
	 * @param T
	 * Entry type
	 * @param name
	 * The name of the entry to watch (implicitly within the [modid][getModid] of this
	 * instance
	 * @param registryType
	 * A [ResourceKey] for the registry in question
	 * @param callback
	 * The callback to invoke, which will be passed the created entry object
	 * @return This [AbstractRegistrate] instance
	 */
	open fun <R : Any, T : R> addRegisterCallback(name: String, registryType: ResourceKey<out Registry<R>>, callback: NonNullConsumer<in T>): S {
		registrationTracker.addRegisterCallback(name, registryType, callback)
		return self()
	}

	/**
	 * Add a callback to be invoked when a certain registry has fully completed registration, i.e. all objects of that
	 * type have been registered.
	 *
	 * @param R
	 * The registry type
	 * @param registryType
	 * A [ResourceKey] for the registry in question
	 * @param callback
	 * The callback to invoke
	 * @return This [AbstractRegistrate] instance
	 */
	open fun <R : Any> addRegisterCallback(registryType: ResourceKey<out Registry<R>>, callback: Runnable): S {
		registrationTracker.addAfterRegisterCallback<R>(registryType, callback)
		return self()
	}

	/**
	 * Check if a certain registry has completed registration.
	 *
	 * @param R
	 * The registry type
	 * @param registryType
	 * A [ResourceKey] for the registry in question
	 * @return `true` iff the given registry has finished the registration step
	 */
	open fun <R : Any> isRegistered(registryType: ResourceKey<out Registry<R>>): Boolean = registrationTracker.isRegistered<R>(registryType)

	/**
	 * Get the data provider instance for a given [GeneratorType]. Only works within datagen context, not during
	 * registration or init.
	 *
	 * @param P
	 * The type of the provider
	 * @param type
	 * A [GeneratorType] representing the desired provider
	 * @return An [Optional] holding the provider, or empty if this provider was not registered. This can happen
	 * if datagen is run only for client or server providers.
	 * @throws IllegalStateException
	 * if datagen has not started yet
	 */
	open fun <P : Any> getDataProvider(type: GeneratorType<P>): Optional<P> {
		val provider = this.provider
		if (provider != null) {
			return provider.getSubProvider<P>(type)
		}
		throw IllegalStateException("Cannot get data provider before datagen is started")
	}

	/**
	 * Mostly internal, sets the data generator for a certain entry/type combination. This will replace an existing data
	 * gen callback if it exists.
	 *
	 * @param P
	 * The type of provider
	 * @param R
	 * The registry type
	 * @param builder
	 * The builder for the entry
	 * @param type
	 * The [GeneratorType] to generate data for
	 * @param cons
	 * A callback to be invoked during data generation
	 * @return this [AbstractRegistrate]
	 */
	open fun <P : Any, R : Any> setDataGenerator(builder: Builder<R, *, *, *>, type: GeneratorType<out P>, cons: NonNullConsumer<out P>): S =
		this.setDataGenerator<P, R>(builder.name, builder.registryKey, type, cons)

	/**
	 * Mostly internal, removes the data generator for a certain entry/type combination if one was previously
	 * registered.
	 *
	 * @param P
	 * The type of provider
	 * @param R
	 * The registry type
	 * @param builder
	 * The builder for the entry
	 * @param type
	 * The [GeneratorType] to stop generating data for
	 * @return this [AbstractRegistrate]
	 */
	open fun <P : Any, R : Any> removeDataGenerator(builder: Builder<R, *, *, *>, type: GeneratorType<out P>): S = this.removeDataGenerator(builder.name, builder.registryKey, type)

	/**
	 * Mostly internal, sets the data generator for a certain entry/type combination. This will replace an existing data
	 * gen callback if it exists.
	 *
	 * @param P
	 * The type of provider
	 * @param R
	 * The registry type
	 * @param entry
	 * The name of the entry which the provider is for
	 * @param registryType
	 * A [Class] representing the registry type of the entry
	 * @param type
	 * The [GeneratorType] to generate data for
	 * @param cons
	 * A callback to be invoked during data generation
	 * @return this [AbstractRegistrate]
	 */
	open fun <P : Any, R : Any> setDataGenerator(entry: String, registryType: ResourceKey<out Registry<R>>, type: GeneratorType<out P>, cons: NonNullConsumer<out P>): S =
		setDataGenerator<P, R>(entry, registryType, EntryMetadata.DataGenerator<P>(type, cons))

	private fun <P : Any, R : Any> setDataGenerator(entry: String, registryType: ResourceKey<out Registry<R>>, generator: EntryMetadata.DataGenerator<P>): S {
		val existing = datagensByEntry.put(Pair.of(entry, registryType), generator.type, generator)
		if (existing != null) {
			datagens.remove(generator.type, existing.callback)
		}
		return addDataGenerator<P>(generator.type, generator.callback)
	}

	/**
	 * Mostly internal, removes the data generator for a certain entry/type combination if one was previously
	 * registered.
	 *
	 * @param P
	 * The type of provider
	 * @param R
	 * The registry type
	 * @param entry
	 * The name of the entry which the provider is for
	 * @param registryType
	 * The registry type of the entry
	 * @param type
	 * The [GeneratorType] to stop generating data for
	 * @return this [AbstractRegistrate]
	 */
	open fun <P : Any, R : Any> removeDataGenerator(entry: String, registryType: ResourceKey<out Registry<R>>, type: GeneratorType<out P>): S {
		val existing = datagensByEntry.remove(Pair.of(entry, registryType), type)
		if (existing != null) {
			datagens.remove(type, existing.callback)
		}
		return self()
	}

	/**
	 * Add a data generator callback that is not associated with any entry, which can never replace an existing data
	 * generator.
	 *
	 * This is useful to add data generator callbacks for miscellaneous data not strictly associated with an entry.
	 *
	 * @param T
	 * The type of provider
	 * @param type
	 * The [GeneratorType] to generate data for
	 * @param cons
	 * A callback to be invoked during data generation
	 * @return this [AbstractRegistrate]
	 */
	open fun <T : Any> addDataGenerator(type: GeneratorType<out T>, cons: NonNullConsumer<out T>): S {
		if (dataGeneration.get()) {
			check(provider == null) { "Cannot add data generator after construction of root generator" }
			datagens.put(type, cons)
		}
		return self()
	}

	open val dataGenInitializer: DataProviderInitializer by lazy(LazyThreadSafetyMode.NONE, ::DataProviderInitializer)

	@JvmRecord
	private data class RawTranslation(val key: TranslationKey, val declaration: LocalizationDeclaration, val source: TranslationSource)

	private val extraLang = ArrayList<RawTranslation>()
	private val generatedLang = ConcurrentLinkedQueue<RawTranslation>()
	private var sourceLocale: MinecraftLocale? = null
	private val localizationCatalog = LocalizationCatalog()

	/** Configures the source locale for this mod before data generation.  */
	open fun sourceLocale(locale: MinecraftLocale): S {
		sourceLocale = locale
		return self()
	}

	/** Returns the source locale after validating this mod's configuration.  */
	open fun requireSourceLocale(): MinecraftLocale {
		val locale = sourceLocale
		requireNotNull(locale) { "[$modid] Configure sourceLocale before generating languages" }
		return locale
	}

	open fun localizationCatalog(): LocalizationCatalog = localizationCatalog

	open fun collectRawTranslations(registry: TranslationRegistry, locale: MinecraftLocale) {
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
			registry.add(translation.key, locale, translation.declaration, translation.source)
		}
	}

	/**
	 * Add a custom translation mapping using the vanilla style of ResourceLocation -&gt; translation key conversion.
	 *
	 * @param type
	 * Type of the object, this is used as a prefix (e.g.
	 * `["block", "mymod:myblock"] -> "block.mymod.myblock"`)
	 * @param id
	 * ID of the object, which will be converted to a lang key via
	 * [Util.makeDescriptionId]
	 * @param localizedName
	 * (English) translation value
	 * @return A [MutableComponent] representing the translated text
	 */
	open fun addLang(type: String, id: ResourceLocation, localizedName: String): MutableComponent = addRawLang(Util.makeDescriptionId(type, id), localizedName)

	/**
	 * Add a custom translation mapping using the vanilla style of ResourceLocation -&gt; translation key conversion.
	 * Also
	 * appends a suffix to the key.
	 *
	 * @param type
	 * Type of the object, this is used as a prefix (e.g.
	 * `["block", "mymod:myblock"] -> "block.mymod.myblock"`)
	 * @param id
	 * ID of the object, which will be converted to a lang key via
	 * [Util.makeDescriptionId]
	 * @param suffix
	 * A suffix which will be appended to the generated key (separated by a dot)
	 * @param localizedName
	 * (English) translation value
	 * @return A [MutableComponent] representing the translated text
	 */
	open fun addLang(type: String, id: ResourceLocation, suffix: String, localizedName: String): MutableComponent = addRawLang(Util.makeDescriptionId(type, id) + "." + suffix, localizedName)

	/**
	 * Add a custom translation mapping directly to the lang provider.
	 *
	 * @param key
	 * The translation key
	 * @param value
	 * The (English) translation value
	 * @return A [MutableComponent] representing the translated text
	 */
	open fun addRawLang(key: String, value: String): MutableComponent = addRawLang(key, value, TranslationSource.Kind.DECLARED)

	private fun addRawLang(key: String, value: String, kind: TranslationSource.Kind): MutableComponent {
		if (dataGeneration.get()) {
			addLocalization(TranslationKey(modid, key), sourceText(value), kind)
		}
		return Component.translatable(key)
	}

	/** Declares independent text and returns a component that uses its exact translation key.  */
	open fun addLocalization(key: TranslationKey, configure: Consumer<LocalizationDeclaration.Builder>): MutableComponent {
		if (dataGeneration.get()) {
			val builder = LocalizationDeclaration.Builder()
			configure.accept(builder)
			addLocalization(key, builder.build(), TranslationSource.Kind.DECLARED)
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

	open fun collectEntryTranslations(registry: TranslationRegistry, locale: MinecraftLocale) {
		registrationTracker.forEachLocalization { entry, localization ->
			if (localization.kind == TranslationSource.Kind.AUTOMATIC && locale != MinecraftLocale.EN_US) {
				return@forEachLocalization
			}
			val key = TranslationKey(modid, localization.key.get())
			val source = TranslationSource(
				localization.kind,
				"Builder ${entry.id} in ${entry.key!!.registry()}",
			)
			registry.add(key, locale, localization.declaration.get(), source)
		}
	}

	private fun getEntryForGenerator(type: GeneratorType<*>, generator: NonNullConsumer<*>): Pair<String, ResourceKey<out Registry<*>>>? {
		for ((key, declaration) in datagensByEntry.column(type)) {
			if (declaration.callback === generator) return key
		}
		return null
	}

	/** Runs the registered callbacks for the supplied generator. */
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
					DATAGEN_LOGGER.debug(DebugMarkers.DATA, modMessage("Generating data of type {} for entry {} [{}]"), modid, name, entry.left, entry.right.location())
				}
			}
			try {
				(callback as NonNullConsumer<T>).accept(gen)
			} catch (error: Exception) {
				val key = if (debug) entry else getEntryForGenerator(type, callback)
				val message = "[$modid] Data generator ${getTypeName(type)} failed" +
					if (key == null) " ($type)" else " for ${key.left} [${key.right.location()}]"
				if (!skipErrors) throw IllegalStateException(message, error)
				DATAGEN_LOGGER.error(DebugMarkers.DATA, message, error)
			}
		}
	}

	/**
	 * Enable skipping of registry entries and data generators that error during registration/generation.
	 *
	 * **Should only be used for debugging!** `skipErrors(true)` will do nothing outside of a dev
	 * environment.
	 *
	 * @param skipErrors
	 * `true` to skip errors during registration/generation
	 * @return this [AbstractRegistrate]
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
	 * Begin a new object, this is typically used at the beginning of a builder chain. The given name will be used until
	 * this method is called again. This makes it simple to create multiple entries
	 * with the same name, as is often the case with blocks/items, items/entities, and blocks/TEs.
	 *
	 * @param name
	 * The name to use for future entries
	 * @return this [AbstractRegistrate]
	 */
	open fun `object`(name: String): S {
		this.currentName = name
		return self()
	}

	/**
	 * Set the default CreativeModeTab to be passed onto future builders.
	 *
	 * This special case method should be used if your creative tab instance was not created by Registrate, otherwise
	 * use [defaultCreativeTab].
	 *
	 * @param creativeModeTab
	 * The new default CreativeModeTab type
	 * @return This [AbstractRegistrate] instance
	 */
	open fun defaultCreativeTab(creativeModeTab: ResourceKey<CreativeModeTab>): S {
		defaultCreativeModeTab = creativeModeTab
		return self()
	}

	/**
	 * Registers a new modifier callback to be used to modify the given CreativeModeTab.
	 *
	 *
	 * Registers a new callback to be invoked during the [BuildCreativeModeTabContentsEvent] event and
	 * used to modify what items are displayed on the given [CreativeModeTab].
	 *
	 * Calling this method multiple times will add additional callbacks.
	 *
	 * @param creativeModeTab The [CreativeModeTab] to register this callback for
	 * @param modifier        The modifier callback to be registered
	 * @return This [AbstractRegistrate] instance
	 */
	open fun modifyCreativeModeTab(creativeModeTab: ResourceKey<CreativeModeTab>, modifier: Consumer<CreativeModeTabModifier>): S {
		creativeModeTabModifiers.put(creativeModeTab, modifier)
		return self()
	}

	/**
	 * Apply a transformation to this [AbstractRegistrate]. Useful to apply helper methods within a fluent chain,
	 * e.g.
	 *
	 * ```java
	 * public static final RegistryEntry<MyBlock> MY_BLOCK = REGISTRATE.object("my_block")
	 *     .transform(Utils::createMyBlock)
	 *     .get(Block.class);
	 * ```
	 *
	 * @param func
	 * The [function][UnaryOperator] to apply
	 * @return this [AbstractRegistrate]
	 */
	open fun transform(func: NonNullUnaryOperator<S>): S = func.apply(self())

	/**
	 * Apply a transformation to this [AbstractRegistrate]. Similar to [transform],
	 * but for actions that return a builder in-progress. Useful to apply helper methods
	 * within a fluent chain, e.g.
	 *
	 * ```java
	 * public static final RegistryEntry<MyBlock> MY_BLOCK = REGISTRATE.object("my_block")
	 *     .transform(Utils::createMyBlock)
	 *     .lang("My Block") // Can modify the builder afterwards
	 *     .register();
	 * ```
	 *
	 * @param R
	 * Registry type
	 * @param T
	 * Entry type
	 * @param P
	 * Parent type
	 * @param S2
	 * Self type
	 * @param func
	 * The [function][Function] to apply
	 * @return the resultant [Builder]
	 */
	open fun <R : Any, T : R, P : Any, S2 : Builder<R, T, P, S2>> transform(func: NonNullFunction<S, S2>): S2 = func.apply(self())

	/**
	 * Create a builder for a new entry. This is typically not needed, unless you are implementing a
	 * [custom builder type](https://github.com/ModularMCLib/RegistrateLib/wiki/Custom-Builders).
	 *
	 * Uses the currently set name (via [object]) as the name for the new entry, and passes it to the
	 * factory as the first parameter.
	 *
	 * @param R
	 * Registry type
	 * @param T
	 * Entry type
	 * @param P
	 * Parent type
	 * @param S2
	 * Self type
	 * @param factory
	 * The factory to create the builder
	 * @return The [Builder] instance
	 */
	open fun <R : Any, T : R, P : Any, S2 : Builder<R, T, P, S2>> entry(factory: NonNullBiFunction<String, BuilderCallback, S2>): S2 = entry<R, T, P, S2>(
		currentName(),
		NonNullFunction { callback: BuilderCallback -> factory.apply(currentName(), callback) },
	)

	/**
	 * Create a builder for a new entry. This is typically not needed, unless you are implementing a
	 * [custom builder type](https://github.com/ModularMCLib/RegistrateLib/wiki/Custom-Builders).
	 *
	 * @param R
	 * Registry type
	 * @param T
	 * Entry type
	 * @param P
	 * Parent type
	 * @param S2
	 * Self type
	 * @param name
	 * The name to use for the entry
	 * @param factory
	 * The factory to create the builder
	 * @return The [Builder] instance
	 */
	open fun <R : Any, T : R, P : Any, S2 : Builder<R, T, P, S2>> entry(name: String, factory: NonNullFunction<BuilderCallback, S2>): S2 = factory.apply(callback)

	/**
	 * Factory method to accept a completed builder and add it to the registration queue.
	 *
	 * Satisfies the functional interface [BuilderCallback], which is typically given to new builder instances
	 * when they are constructed.
	 *
	 * @param R
	 * Registry type
	 * @param T
	 * Entry type
	 * @param name
	 * Thename of the entry being created
	 * @param type
	 * The registry to add the entry to
	 * @param builder
	 * The builder instance that was used to create this entry. Not used by default implementation,
	 * but custom extensions may use it for some purpose
	 * @param creator
	 * Constructor for the new entry object
	 * @param entryFactory
	 * Optional custom factory to create special [RegistryEntry] types
	 * @return A [RegistryEntry] that will hold the created entry after registration is complete
	 */
	protected open fun <R : Any, T : R> accept(
		name: String,
		type: ResourceKey<out Registry<R>>,
		builder: Builder<R, T, *, *>,
		creator: NonNullSupplier<out T>,
		entryFactory: NonNullFunction<DeferredHolder<R, T>, out RegistryEntry<R, T>>,
	): RegistryEntry<R, T> {
		val generators = datagensByEntry.row(Pair.of<String, ResourceKey<out Registry<*>>>(name, type))
		var localization: EntryLocalization? = null
		var declarations: Collection<EntryMetadata.DataGenerator<*>> =
			mutableListOf<EntryMetadata.DataGenerator<*>>()
		if (builder is AbstractBuilder<*, *, *, *> && name == builder.name && type == builder.registryKey) {
			declarations = builder.dataGenerators()
			localization = builder.localization()
		}
		val entry = registrationTracker.track<R, T>(
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
	 * Returns the declarations for an entry submitted by this Registrate instance.
	 */
	open fun getEntryMetadata(key: ResourceKey<*>): EntryMetadata = registrationTracker.getMetadata(key)

	/**
	 * Helper to create a new registry for custom objects. The returned [ResourceKey] can be used immediately in
	 * methods like [simple][simple] or
	 * [generic][generic].
	 *
	 * Alternatively, a custom [builder][Builder] can be created.
	 *
	 * This method will automatically subscribe to the [NewRegistryEvent] and create the registry at the proper
	 * time. Thus, the new registry will not exist immediately after this is called.
	 *
	 * @param R
	 * The type of object the new registry will contain
	 * @param name
	 * The ID of this registry
	 * @param builder
	 * A function to create the [RegistryBuilder] that defines the other properties/behaviors of
	 * the created registry
	 * @return A [resource key][ResourceKey] referencing the to-be-created registry.
	 */
	open fun <R : Any> makeRegistry(name: String, builder: Function<ResourceKey<Registry<R>>, RegistryBuilder<R>>): ResourceKey<Registry<R>> {
		val registryId = ResourceKey.createRegistryKey<R>(ResourceLocation.fromNamespaceAndPath(modid, name))
		addModListener<NewRegistryEvent>(
			this,
			NewRegistryEvent::class.java,
			Consumer { e: NewRegistryEvent -> e.register<R>(builder.apply(registryId).create()) },
		)
		return registryId
	}

	/**
	 * Registers the given registry key as an unsynced datapack registry, which will cause data to be loaded from
	 * a datapack folder based on the registry's name. The datapack registry is not required to be present
	 * on clients when connecting to servers with the mod/registry.
	 *
	 * Data JSONs will be loaded from `data/<datapack_namespace>/modid/registryname/`, where `modid` is the
	 * namespace of the registry key.
	 *
	 * @param name  The ID of this registry
	 * @param codec The codec to be used for loading data from datapacks on servers
	 * @see .makeDatapackRegistry
	 */
	open fun <R : Any> makeDatapackRegistry(name: String, codec: Codec<R>): ResourceKey<Registry<R>> = makeDatapackRegistry<R>(name, codec, null)

	/**
	 * Registers the registry key as a datapack registry, which will cause data to be loaded from
	 * a datapack folder based on the registry's name.
	 *
	 * Data JSONs will be loaded from `data/<datapack_namespace>/modid/registryname/`, where `modid` is the
	 * namespace of the registry key.
	 *
	 * @param name         The ID of this registry
	 * @param codec        The codec to be used for loading data from datapacks on servers
	 * @param networkCodec The codec to be used for syncing loaded data to clients.
	 * If `networkCodec` is null, data will not be synced, and clients are not required to
	 * have this
	 * datapack registry to join a server.
	 *
	 * If `networkCodec` is not null, clients must have this datapack registry/mod
	 * when joining a server that has this datapack registry/mod.
	 * The data will be synced using the network codec and accessible via
	 * [ClientPacketListener.registryAccess].
	 * @see .makeDatapackRegistry
	 */
	open fun <R : Any> makeDatapackRegistry(name: String, codec: Codec<R>, networkCodec: Codec<R>?): ResourceKey<Registry<R>> {
		val registryId = ResourceKey.createRegistryKey<R>(ResourceLocation.fromNamespaceAndPath(modid, name))
		addModListener<NewRegistry>(
			this,
			NewRegistry::class.java,
			Consumer { event: NewRegistry -> event.dataPackRegistry<R>(registryId, codec, networkCodec) },
		)
		return registryId
	}

	/* === Builder helpers === */
	// Generic
	open fun <R : Any, T : R> simple(registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): RegistryEntry<R, T> = simple(currentName(), registryType, factory)

	open fun <R : Any, T : R> simple(name: String, registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): RegistryEntry<R, T> =
		simple<R, T, AbstractRegistrate<S>>(this, name, registryType, factory)

	open fun <R : Any, T : R, P : Any> simple(parent: P, registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): RegistryEntry<R, T> =
		simple(parent, currentName(), registryType, factory)

	open fun <R : Any, T : R, P : Any> simple(parent: P, name: String, registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): RegistryEntry<R, T> =
		generic(parent, name, registryType, factory).register()

	open fun <R : Any, T : R> generic(registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): NoConfigBuilder<R, T, S> = generic(self(), registryType, factory)

	open fun <R : Any, T : R> generic(name: String, registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): NoConfigBuilder<R, T, S> = generic(self(), name, registryType, factory)

	open fun <R : Any, T : R, P : Any> generic(parent: P, registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): NoConfigBuilder<R, T, P> =
		generic(parent, currentName(), registryType, factory)

	open fun <R : Any, T : R, P : Any> generic(parent: P, name: String, registryType: ResourceKey<Registry<R>>, factory: NonNullSupplier<T>): NoConfigBuilder<R, T, P> =
		entry<R, T, P, NoConfigBuilder<R, T, P>>(
			name,
			NonNullFunction { callback: BuilderCallback ->
				NoConfigBuilder<R, T, P>(
					this,
					parent,
					name,
					callback,
					registryType,
					factory,
				)
			},
		)

	// Items
	open fun <T : Item> item(factory: NonNullFunction<Item.Properties, T>): ItemBuilder<T, S> = item(self(), factory)

	open fun <T : Item> item(name: String, factory: NonNullFunction<Item.Properties, T>): ItemBuilder<T, S> = item(self(), name, factory)

	open fun <T : Item, P : Any> item(parent: P, factory: NonNullFunction<Item.Properties, T>): ItemBuilder<T, P> = item(parent, currentName(), factory)

	open fun <T : Item, P : Any> item(parent: P, name: String, factory: NonNullFunction<Item.Properties, T>): ItemBuilder<T, P> = entry<Item, T, P, ItemBuilder<T, P>>(
		name,
		NonNullFunction { callback: BuilderCallback ->
			ItemBuilder.create<T, P>(this, parent, name, callback, factory)
				.tab(defaultCreativeModeTab)
		},
	)

	// Blocks
	open fun <T : Block> block(factory: NonNullFunction<BlockBehaviour.Properties, T>): BlockBuilder<T, S> = block(self(), factory)

	open fun <T : Block> block(name: String, factory: NonNullFunction<BlockBehaviour.Properties, T>): BlockBuilder<T, S> = block(self(), name, factory)

	open fun <T : Block, P : Any> block(parent: P, factory: NonNullFunction<BlockBehaviour.Properties, T>): BlockBuilder<T, P> = block(parent, currentName(), factory)

	open fun <T : Block, P : Any> block(parent: P, name: String, factory: NonNullFunction<BlockBehaviour.Properties, T>): BlockBuilder<T, P> = entry<Block, T, P, BlockBuilder<T, P>>(
		name,
		NonNullFunction { callback: BuilderCallback ->
			BlockBuilder.create<T, P>(
				this,
				parent,
				name,
				callback,
				factory,
			)
		},
	)

	// Entities
	open fun <T : Entity> entity(factory: EntityFactory<T>, classification: MobCategory): EntityBuilder<T, S> = entity(self(), factory, classification)

	open fun <T : Entity> entity(name: String, factory: EntityFactory<T>, classification: MobCategory): EntityBuilder<T, S> = entity(self(), name, factory, classification)

	open fun <T : Entity, P : Any> entity(parent: P, factory: EntityFactory<T>, classification: MobCategory): EntityBuilder<T, P> = entity(parent, currentName(), factory, classification)

	open fun <T : Entity, P : Any> entity(parent: P, name: String, factory: EntityFactory<T>, classification: MobCategory): EntityBuilder<T, P> =
		entry<EntityType<*>, EntityType<T>, P, EntityBuilder<T, P>>(
			name,
			NonNullFunction { callback: BuilderCallback ->
				EntityBuilder.create<T, P>(
					this,
					parent,
					name,
					callback,
					factory,
					classification,
				)
			},
		)

	// Block Entities
	open fun <T : BlockEntity> blockEntity(factory: BlockEntityFactory<T>): BlockEntityBuilder<T, S> = blockEntity(self(), factory)

	open fun <T : BlockEntity> blockEntity(name: String, factory: BlockEntityFactory<T>): BlockEntityBuilder<T, S> = blockEntity(self(), name, factory)

	open fun <T : BlockEntity, P : Any> blockEntity(parent: P, factory: BlockEntityFactory<T>): BlockEntityBuilder<T, P> = blockEntity(parent, currentName(), factory)

	open fun <T : BlockEntity, P : Any> blockEntity(parent: P, name: String, factory: BlockEntityFactory<T>): BlockEntityBuilder<T, P> =
		entry<BlockEntityType<*>, BlockEntityType<T>, P, BlockEntityBuilder<T, P>>(
			name,
			NonNullFunction { callback: BuilderCallback ->
				BlockEntityBuilder.create<T, P>(
					this,
					parent,
					name,
					callback,
					factory,
				)
			},
		)

	// Fluids
	open fun fluid(): FluidBuilder<Flowing, S> = fluid(self())

	open fun fluid(typeFactory: FluidTypeFactory): FluidBuilder<Flowing, S> = fluid(self(), typeFactory)

	open fun fluid(fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, S> = fluid(self(), fluidType)

	open fun fluid(stillTexture: ResourceLocation, flowingTexture: ResourceLocation): FluidBuilder<Flowing, S> = fluid(self(), stillTexture, flowingTexture)

	open fun fluid(stillTexture: ResourceLocation, flowingTexture: ResourceLocation, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, S> = fluid(self(), stillTexture, flowingTexture, typeFactory)

	open fun fluid(stillTexture: ResourceLocation, flowingTexture: ResourceLocation, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, S> =
		fluid(self(), stillTexture, flowingTexture, fluidType)

	open fun <T : BaseFlowingFluid> fluid(stillTexture: ResourceLocation, flowingTexture: ResourceLocation, fluidFactory: FluidFactory<T>): FluidBuilder<T, S> =
		fluid(self(), stillTexture, flowingTexture, fluidFactory)

	open fun <T : BaseFlowingFluid> fluid(stillTexture: ResourceLocation, flowingTexture: ResourceLocation, typeFactory: FluidTypeFactory, fluidFactory: FluidFactory<T>): FluidBuilder<T, S> =
		fluid(self(), stillTexture, flowingTexture, typeFactory, fluidFactory)

	open fun <T : BaseFlowingFluid> fluid(stillTexture: ResourceLocation, flowingTexture: ResourceLocation, fluidType: NonNullSupplier<FluidType>, fluidFactory: FluidFactory<T>): FluidBuilder<T, S> =
		fluid(self(), stillTexture, flowingTexture, fluidType, fluidFactory)

	open fun fluid(name: String): FluidBuilder<Flowing, S> = fluid(self(), name)

	open fun fluid(name: String, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, S> = fluid(self(), name, typeFactory)

	open fun fluid(name: String, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, S> = fluid(self(), name, fluidType)

	open fun fluid(name: String, stillTexture: ResourceLocation, flowingTexture: ResourceLocation): FluidBuilder<Flowing, S> = fluid(self(), name, stillTexture, flowingTexture)

	open fun fluid(name: String, stillTexture: ResourceLocation, flowingTexture: ResourceLocation, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, S> =
		fluid(self(), name, stillTexture, flowingTexture, typeFactory)

	open fun fluid(name: String, stillTexture: ResourceLocation, flowingTexture: ResourceLocation, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, S> =
		fluid(self(), name, stillTexture, flowingTexture, fluidType)

	open fun <T : BaseFlowingFluid> fluid(name: String, stillTexture: ResourceLocation, flowingTexture: ResourceLocation, fluidFactory: FluidFactory<T>): FluidBuilder<T, S> =
		fluid(self(), name, stillTexture, flowingTexture, fluidFactory)

	open fun <T : BaseFlowingFluid> fluid(
		name: String,
		stillTexture: ResourceLocation,
		flowingTexture: ResourceLocation,
		typeFactory: FluidTypeFactory,
		fluidFactory: FluidFactory<T>,
	): FluidBuilder<T, S> = fluid(self(), name, stillTexture, flowingTexture, typeFactory, fluidFactory)

	open fun <T : BaseFlowingFluid> fluid(
		name: String,
		stillTexture: ResourceLocation,
		flowingTexture: ResourceLocation,
		fluidType: NonNullSupplier<FluidType>,
		fluidFactory: FluidFactory<T>,
	): FluidBuilder<T, S> = fluid(self(), name, stillTexture, flowingTexture, fluidType, fluidFactory)

	open fun <P : Any> fluid(parent: P): FluidBuilder<Flowing, P> = fluid(parent, currentName())

	open fun <P : Any> fluid(parent: P, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, P> = fluid(parent, currentName(), typeFactory)

	open fun <P : Any> fluid(parent: P, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, P> = fluid(parent, currentName(), fluidType)

	open fun <P : Any> fluid(parent: P, stillTexture: ResourceLocation, flowingTexture: ResourceLocation): FluidBuilder<Flowing, P> = fluid(parent, currentName(), stillTexture, flowingTexture)

	open fun <P : Any> fluid(parent: P, stillTexture: ResourceLocation, flowingTexture: ResourceLocation, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, P> =
		fluid(parent, currentName(), stillTexture, flowingTexture, typeFactory)

	open fun <P : Any> fluid(parent: P, stillTexture: ResourceLocation, flowingTexture: ResourceLocation, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, P> =
		fluid(parent, currentName(), stillTexture, flowingTexture, fluidType)

	open fun <T : BaseFlowingFluid, P : Any> fluid(parent: P, stillTexture: ResourceLocation, flowingTexture: ResourceLocation, fluidFactory: FluidFactory<T>): FluidBuilder<T, P> =
		fluid(parent, currentName(), stillTexture, flowingTexture, fluidFactory)

	open fun <T : BaseFlowingFluid, P : Any> fluid(
		parent: P,
		stillTexture: ResourceLocation,
		flowingTexture: ResourceLocation,
		typeFactory: FluidTypeFactory,
		fluidFactory: FluidFactory<T>,
	): FluidBuilder<T, P> = fluid(parent, currentName(), stillTexture, flowingTexture, typeFactory, fluidFactory)

	open fun <T : BaseFlowingFluid, P : Any> fluid(
		parent: P,
		stillTexture: ResourceLocation,
		flowingTexture: ResourceLocation,
		fluidType: NonNullSupplier<FluidType>,
		fluidFactory: FluidFactory<T>,
	): FluidBuilder<T, P> = fluid(parent, currentName(), stillTexture, flowingTexture, fluidType, fluidFactory)

	open fun <P : Any> fluid(parent: P, name: String): FluidBuilder<Flowing, P> = fluid(parent, name, defaultStillTexture(), defaultFlowingTexture())

	open fun <P : Any> fluid(parent: P, name: String, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, P> = fluid(parent, name, defaultStillTexture(), defaultFlowingTexture(), typeFactory)

	open fun <P : Any> fluid(parent: P, name: String, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, P> = fluid(parent, name, defaultStillTexture(), defaultFlowingTexture(), fluidType)

	private fun defaultStillTexture(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(modid, "block/" + currentName() + "_still")

	private fun defaultFlowingTexture(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(modid, "block/" + currentName() + "_flow")

	open fun <P : Any> fluid(parent: P, name: String, stillTexture: ResourceLocation, flowingTexture: ResourceLocation): FluidBuilder<Flowing, P> = entry<Fluid, Flowing, P, FluidBuilder<Flowing, P>>(
		name,
		NonNullFunction { callback: BuilderCallback ->
			FluidBuilder.create<P>(
				this,
				parent,
				name,
				callback,
				FluidTypeFactory { properties: FluidType.Properties? ->
					FluidType(properties)
				},
			)
		},
	).model(stillTexture, flowingTexture)

	open fun <P : Any> fluid(parent: P, name: String, stillTexture: ResourceLocation, flowingTexture: ResourceLocation, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, P> =
		entry<Fluid, Flowing, P, FluidBuilder<Flowing, P>>(
			name,
			NonNullFunction { callback: BuilderCallback ->
				FluidBuilder.create<P>(
					this,
					parent,
					name,
					callback,
					typeFactory,
				)
			},
		).model(stillTexture, flowingTexture)

	open fun <P : Any> fluid(parent: P, name: String, stillTexture: ResourceLocation, flowingTexture: ResourceLocation, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, P> =
		entry<Fluid, Flowing, P, FluidBuilder<Flowing, P>>(
			name,
			NonNullFunction { callback: BuilderCallback ->
				FluidBuilder.create<P>(
					this,
					parent,
					name,
					callback,
					fluidType,
				)
			},
		).model(stillTexture, flowingTexture)

	open fun <T : BaseFlowingFluid, P : Any> fluid(parent: P, name: String, stillTexture: ResourceLocation, flowingTexture: ResourceLocation, fluidFactory: FluidFactory<T>): FluidBuilder<T, P> =
		entry<Fluid, T, P, FluidBuilder<T, P>>(
			name,
			NonNullFunction { callback: BuilderCallback ->
				FluidBuilder.create<T, P>(
					this,
					parent,
					name,
					callback,
					fluidFactory,
				)
			},
		).model(stillTexture, flowingTexture)

	open fun <T : BaseFlowingFluid, P : Any> fluid(
		parent: P,
		name: String,
		stillTexture: ResourceLocation,
		flowingTexture: ResourceLocation,
		typeFactory: FluidTypeFactory,
		fluidFactory: FluidFactory<T>,
	): FluidBuilder<T, P> = entry<Fluid, T, P, FluidBuilder<T, P>>(
		name,
		NonNullFunction { callback: BuilderCallback ->
			FluidBuilder.create<T, P>(
				this,
				parent,
				name,
				callback,
				typeFactory,
				fluidFactory,
			)
		},
	).model(stillTexture, flowingTexture)

	open fun <T : BaseFlowingFluid, P : Any> fluid(
		parent: P,
		name: String,
		stillTexture: ResourceLocation,
		flowingTexture: ResourceLocation,
		fluidType: NonNullSupplier<FluidType>,
		fluidFactory: FluidFactory<T>,
	): FluidBuilder<T, P> = entry<Fluid, T, P, FluidBuilder<T, P>>(
		name,
		NonNullFunction { callback: BuilderCallback ->
			FluidBuilder.create<T, P>(
				this,
				parent,
				name,
				callback,
				fluidType,
				fluidFactory,
			)
		},
	).model(stillTexture, flowingTexture)

	// Menu
	open fun <T : AbstractContainerMenu, SC> menu(factory: MenuFactory<T>, screenFactory: NonNullSupplier<ScreenFactory<T, SC>>): MenuBuilder<T, SC, S> where SC : Screen, SC : MenuAccess<T> =
		menu<T, SC>(currentName(), factory, screenFactory)

	open fun <T : AbstractContainerMenu, SC> menu(
		name: String,
		factory: MenuFactory<T>,
		screenFactory: NonNullSupplier<ScreenFactory<T, SC>>,
	): MenuBuilder<T, SC, S> where SC : Screen, SC : MenuAccess<T> = menu<T, SC, S>(self(), name, factory, screenFactory)

	open fun <T : AbstractContainerMenu, SC, P : Any> menu(
		parent: P,
		factory: MenuFactory<T>,
		screenFactory: NonNullSupplier<ScreenFactory<T, SC>>,
	): MenuBuilder<T, SC, P> where SC : Screen, SC : MenuAccess<T> = menu<T, SC, P>(parent, currentName(), factory, screenFactory)

	open fun <T : AbstractContainerMenu, SC, P : Any> menu(
		parent: P,
		name: String,
		factory: MenuFactory<T>,
		screenFactory: NonNullSupplier<ScreenFactory<T, SC>>,
	): MenuBuilder<T, SC, P> where SC : Screen, SC : MenuAccess<T> = entry<MenuType<*>, MenuType<T>, P, MenuBuilder<T, SC, P>>(
		name,
		NonNullFunction { callback: BuilderCallback ->
			MenuBuilder<T, SC, P>(
				this,
				parent,
				name,
				callback,
				factory,
				screenFactory,
			)
		},
	)

	open fun <T : AbstractContainerMenu, SC> menu(factory: ForgeMenuFactory<T>, screenFactory: NonNullSupplier<ScreenFactory<T, SC>>): MenuBuilder<T, SC, S> where SC : Screen, SC : MenuAccess<T> =
		menu<T, SC>(currentName(), factory, screenFactory)

	open fun <T : AbstractContainerMenu, SC> menu(name: String, factory: ForgeMenuFactory<T>, screenFactory: NonNullSupplier<ScreenFactory<T, SC>>) where SC : Screen, SC : MenuAccess<T> =
		menu(self(), name, factory, screenFactory)

	open fun <T : AbstractContainerMenu, SC, P : Any> menu(parent: P, factory: ForgeMenuFactory<T>, screenFactory: NonNullSupplier<ScreenFactory<T, SC>>) where SC : Screen, SC : MenuAccess<T> =
		menu(parent, currentName(), factory, screenFactory)

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

	// Creative Tab
	open fun defaultCreativeTab(): NoConfigBuilder<CreativeModeTab, CreativeModeTab, S> = defaultCreativeTab(self())

	open fun defaultCreativeTab(name: String): NoConfigBuilder<CreativeModeTab, CreativeModeTab, S> = defaultCreativeTab(self(), name)

	open fun <P : Any> defaultCreativeTab(parent: P): NoConfigBuilder<CreativeModeTab, CreativeModeTab, P> = defaultCreativeTab(parent, currentName())

	open fun <P : Any> defaultCreativeTab(parent: P, name: String): NoConfigBuilder<CreativeModeTab, CreativeModeTab, P> = defaultCreativeTab(
		parent,
		name,
		Consumer { tab: CreativeModeTab.Builder? ->
		},
	)

	open fun defaultCreativeTab(config: Consumer<CreativeModeTab.Builder>): NoConfigBuilder<CreativeModeTab, CreativeModeTab, S> = defaultCreativeTab(self(), config)

	open fun defaultCreativeTab(name: String, config: Consumer<CreativeModeTab.Builder>): NoConfigBuilder<CreativeModeTab, CreativeModeTab, S> = defaultCreativeTab(self(), name, config)

	open fun <P : Any> defaultCreativeTab(parent: P, config: Consumer<CreativeModeTab.Builder>): NoConfigBuilder<CreativeModeTab, CreativeModeTab, P> =
		defaultCreativeTab(parent, currentName(), config)

	open fun <P : Any> defaultCreativeTab(parent: P, name: String, config: Consumer<CreativeModeTab.Builder>): NoConfigBuilder<CreativeModeTab, CreativeModeTab, P> {
		this.defaultCreativeModeTab = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			ResourceLocation.fromNamespaceAndPath(this.modid, name),
		)
		return this.generic<CreativeModeTab, CreativeModeTab, P>(
			parent,
			name,
			Registries.CREATIVE_MODE_TAB,
		) {
			val builder = CreativeModeTab.builder()
				.icon {
					getAll<Item, Item>(Registries.ITEM).firstOrNull()?.get()?.let(::ItemStack) ?: ItemStack.EMPTY
				}
				.title(
					addRawLang(
						Util.makeDescriptionId("itemGroup", this.defaultCreativeModeTab.location()),
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

		/** Enables development diagnostics when NeoForge is running outside production. */
		@get:JvmStatic
		val isDevEnvironment: Boolean
			get() = !FMLEnvironment.production
	}
}
