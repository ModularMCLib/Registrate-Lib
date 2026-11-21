package com.modularmc.registrate.builders

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.builders.base.AbstractBuilder
import com.modularmc.registrate.builders.base.BuilderCallback
import com.modularmc.registrate.internal.event.OneTimeEventReceiver.Companion.addModListener
import com.modularmc.registrate.internal.util.RegistrateDistExecutor.unsafeRunWhenOn
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.providers.DataGenContext
import com.modularmc.registrate.providers.RegistrateLangProvider
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.providers.generators.RegistrateItemModelGenerator
import com.modularmc.registrate.providers.generators.RegistrateRecipeProvider
import com.modularmc.registrate.util.CreativeModeTabModifier
import com.modularmc.registrate.util.entry.ItemEntry
import com.modularmc.registrate.util.entry.RegistryEntry
import com.modularmc.registrate.util.nullness.*

import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.data.models.model.ModelTemplates
import net.minecraft.client.data.models.model.TextureMapping
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap

import java.util.function.Consumer
import java.util.function.Supplier

/**
 * A builder for items, allows for customization of the [Item.Properties] and configuration of data associated
 * with items (models, recipes, etc.).
 *
 * @param T
 * The type of item being built
 * @param P
 * Parent object type
 */
open class ItemBuilder<T : Item, P : Any> protected constructor(
	owner: AbstractRegistrate<*>,
	parent: P,
	name: String,
	callback: BuilderCallback,
	private val factory: NonNullFunction<Item.Properties, T>,
) : AbstractBuilder<Item, T, P, ItemBuilder<T, P>>(owner, parent, name, callback, Registries.ITEM) {
	private var initialProperties = NonNullSupplier { Item.Properties() }
	private var propertiesCallback: NonNullFunction<Item.Properties, Item.Properties> = NonNullUnaryOperator.identity()

	private val creativeModeTabs: MutableMap<ResourceKey<CreativeModeTab>, NonNullBiConsumer<DataGenContext<Item, T>, CreativeModeTabModifier>> =
		Object2ObjectLinkedOpenHashMap()

	/**
	 * Modify the properties of the item. Modifications are done lazily, but the passed function is composed with the
	 * current one, and as such this method can be called multiple times to perform
	 * different operations.
	 *
	 *
	 * If a different properties instance is returned, it will replace the existing one entirely.
	 *
	 * @param func
	 * The action to perform on the properties
	 * @return this [ItemBuilder]
	 */
	open fun properties(func: NonNullUnaryOperator<Item.Properties>): ItemBuilder<T, P> {
		propertiesCallback = propertiesCallback.andThen(func)
		return this
	}

	/**
	 * Replace the initial state of the item properties, without replacing or removing any modifications done via
	 * [properties].
	 *
	 * @param properties
	 * A supplier to to create the initial properties
	 * @return this [ItemBuilder]
	 */
	open fun initialProperties(properties: NonNullSupplier<Item.Properties>): ItemBuilder<T, P> {
		initialProperties = properties
		return this
	}

	/** Adds this item to a tab, with an optional callback to customize its display. */
	@JvmOverloads
	open fun tab(
		tab: ResourceKey<CreativeModeTab>,
		modifier: NonNullBiConsumer<DataGenContext<Item, T>, CreativeModeTabModifier> = NonNullBiConsumer { item: DataGenContext<Item, T>, modifier: CreativeModeTabModifier ->
			modifier.accept(item)
		},
	): ItemBuilder<T, P> {
		creativeModeTabs[tab] = modifier // Should we get the current value in the map [if one exists] and
		// .andThen() the 2 together? right now we replace any consumer that
		// currently exists
		return this
	}

	/**
	 * Removes the modifier from this builder from the given [CreativeModeTab].
	 *
	 * @param tab A [ResourceKey] representing the [CreativeModeTab] to remove the modifier from
	 * @return This builder
	 */
	open fun removeTab(tab: ResourceKey<CreativeModeTab>): ItemBuilder<T, P> {
		creativeModeTabs.remove(tab)
		return this
	}

	/**
	 * Assign the default model to this item, which is simply a generated model with a single texture of the same name.
	 *
	 * @return this [ItemBuilder]
	 */
	open fun defaultModel(): ItemBuilder<T, P> = model {
		NonNullBiConsumer { ctx, provider ->
			provider.generateFlatItem(ctx.get(), provider.modItemTexture(ctx.name))
		}
	}

	/**
	 * Generates the default item model with the supplied layer tints.
	 *
	 * @param tints The tint sources in model layer order
	 * @return this [ItemBuilder]
	 */
	open fun defaultModel(vararg tints: ItemTintSource): ItemBuilder<T, P> = model {
		NonNullBiConsumer { ctx, provider ->
			val item = ctx.get()
			provider.generateFlatItem(item, ModelTemplates.FLAT_ITEM, TextureMapping.layer0(item), *tints)
		}
	}

	/**
	 * Configure the model for this item.
	 *
	 * @param cons
	 * The callback which will be invoked during data creation
	 * @return this [ItemBuilder]
	 * @see .setData
	 */
	open fun model(cons: NonNullSupplier<NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator>>): ItemBuilder<T, P> {
		val model: NonNullSupplier<NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator>> =
			cons.lazy()
		return setData(
			ProviderType.ITEM_MODEL,
		) { context: DataGenContext<Item, T>, generator: RegistrateItemModelGenerator ->
			model.get().accept(context, generator)
		}
	}

	/**
	 * Assign the default translation, as specified by
	 * [RegistrateLangProvider.getAutomaticName]. This is
	 * the default, so it is generally
	 * not necessary to call, unless for undoing previous changes.
	 *
	 * @return this [ItemBuilder]
	 */
	open fun defaultLang(): ItemBuilder<T, P> = lang { obj: T -> obj.descriptionId }

	/** Configures the source text and translations of this entry.  */
	open fun localization(configure: Consumer<LocalizationDeclaration.Builder>): ItemBuilder<T, P> = localization({ obj: T -> obj.descriptionId }, configure)

	/**
	 * Set the translation for this item.
	 *
	 * @param name
	 * A localized English name
	 * @return this [ItemBuilder]
	 */
	open fun lang(name: String): ItemBuilder<T, P> = lang({ obj: T -> obj.descriptionId }, name)

	/**
	 * Configure the recipe(s) for this item.
	 *
	 * @param cons
	 * The callback which will be invoked during data generation.
	 * @return this [ItemBuilder]
	 * @see .setData
	 */
	open fun recipe(cons: NonNullBiConsumer<DataGenContext<Item, T>, RegistrateRecipeProvider>) = setData(ProviderType.RECIPE, cons)

	/**
	 * Add burn time for the item
	 *
	 * @param tick time in ticks for this item to burn in furnace.
	 */
	open fun burnTime(tick: Int): ItemBuilder<T, P> = dataMap(NeoForgeDataMaps.FURNACE_FUELS, FurnaceFuel(tick))

	/**
	 * Add compost chance for the item
	 *
	 * @param chance chance for composter to increase one level when composting this item.
	 */
	open fun compostable(chance: Float): ItemBuilder<T, P> = dataMap(NeoForgeDataMaps.COMPOSTABLES, Compostable(chance))

	private var clientExtension: NonNullSupplier<Supplier<IClientItemExtensions>>? = null

	init {
		onRegister(
			NonNullConsumer {
				creativeModeTabs.forEach { (creativeModeTab, consumer) ->
					owner.modifyCreativeModeTab(
						creativeModeTab,
					) { modifier: CreativeModeTabModifier ->
						consumer.accept(
							DataGenContext.from(this),
							modifier,
						)
					}
				}
				creativeModeTabs.clear() // this registration should only fire once, to doubly ensure this, clear the map
			},
		)
	}

	/**
	 * Register a client extension for this item. The [IClientItemExtensions] instance can be shared across many
	 * items.
	 *
	 * @param clientExtension
	 * The client extension to register for this item
	 * @return this [ItemBuilder]
	 */
	open fun clientExtension(clientExtension: NonNullSupplier<Supplier<IClientItemExtensions>>): ItemBuilder<T, P> {
		if (this.clientExtension == null) {
			unsafeRunWhenOn(Dist.CLIENT) { Runnable { this.registerClientExtension() } }
		}
		this.clientExtension = clientExtension
		return this
	}

	protected open fun registerClientExtension() {
		addModListener(
			owner,
			RegisterClientExtensionsEvent::class.java,
			Consumer { e: RegisterClientExtensionsEvent ->
				val clientExtension = this.clientExtension
				if (clientExtension != null) {
					e.registerItem(clientExtension.get().get(), entry)
				}
			},
		)
	}

	/**
	 * Assign [TagKey]`s` to this item. Multiple calls will add additional tags.
	 *
	 * @param tags
	 * The tag to assign
	 * @return this [ItemBuilder]
	 */
	@SafeVarargs
	fun tag(vararg tags: TagKey<Item>) = tag(ProviderType.ITEM_TAGS, *tags)

	override fun createEntry(): T {
		var properties = this.initialProperties.get()
		properties = propertiesCallback.apply(properties)
		return factory.apply(properties.setId(resourceKey))
	}

	override fun createEntryWrapper(delegate: DeferredHolder<Item, T>): RegistryEntry<Item, T> = ItemEntry(owner, delegate)

	override fun register(): ItemEntry<T> = super.register() as ItemEntry<T>

	companion object {
		/**
		 * Create a new [ItemBuilder] and configure data. Used in lieu of adding side-effects to constructor, so that
		 * alternate initialization strategies can be done in subclasses.
		 *
		 *
		 * The item will be assigned the following data:
		 *
		 *  * A simple generated model with one texture (via [.defaultModel])
		 *  * The default translation (via [.defaultLang])
		 *
		 *
		 * @param <T>
		 * The type of the builder
		 * @param <P>
		 * Parent object type
		 * @param owner
		 * The owning [AbstractRegistrate] object
		 * @param parent
		 * The parent object
		 * @param name
		 * Name of the entry being built
		 * @param callback
		 * A callback used to actually register the built entry
		 * @param factory
		 * Factory to create the item
		 * @return A new [ItemBuilder] with reasonable default data generators.
		</P></T> */
		@JvmStatic
		fun <T : Item, P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, factory: NonNullFunction<Item.Properties, T>) =
			ItemBuilder(owner, parent, name, callback, factory)
				.defaultModel().defaultLang()
	}
}
