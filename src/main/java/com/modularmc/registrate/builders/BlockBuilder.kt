package com.modularmc.registrate.builders

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.builders.base.AbstractBuilder
import com.modularmc.registrate.builders.base.BuilderCallback
import com.modularmc.registrate.internal.event.OneTimeEventReceiver.Companion.addModListener
import com.modularmc.registrate.internal.util.RegistrateDistExecutor.unsafeRunWhenOn
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.providers.DataGenContext
import com.modularmc.registrate.providers.RegistrateLangProvider
import com.modularmc.registrate.providers.RegistrateTagsProvider
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.providers.generators.RegistrateBlockModelGenerator
import com.modularmc.registrate.providers.generators.RegistrateItemModelGenerator
import com.modularmc.registrate.providers.generators.RegistrateRecipeProvider
import com.modularmc.registrate.providers.loot.RegistrateBlockLootTables
import com.modularmc.registrate.providers.loot.RegistrateLootTableProvider
import com.modularmc.registrate.util.entry.BlockEntry
import com.modularmc.registrate.util.entry.RegistryEntry
import com.modularmc.registrate.util.nullness.*

import net.minecraft.client.color.block.BlockColor
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
import net.neoforged.neoforge.registries.DeferredHolder

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * A builder for blocks, allows for customization of the [Block.Properties], creation of block items, and
 * configuration of data associated with blocks (loot tables, recipes, etc.).
 *
 * @param T
 * The type of block being built
 * @param P
 * Parent object type
 */
open class BlockBuilder<T : Block, P : Any> protected constructor(
	owner: AbstractRegistrate<*>,
	parent: P,
	name: String,
	callback: BuilderCallback,
	private val factory: NonNullFunction<BlockBehaviour.Properties, T>,
	private var initialProperties: NonNullSupplier<BlockBehaviour.Properties>,
) : AbstractBuilder<Block, T, P, BlockBuilder<T, P>>(owner, parent, name, callback, Registries.BLOCK) {
	private var propertiesCallback: NonNullFunction<BlockBehaviour.Properties, BlockBehaviour.Properties> =
		NonNullUnaryOperator.identity<BlockBehaviour.Properties>()

	private var colorHandler: NonNullSupplier<Supplier<BlockColor>>? = null

	/**
	 * Modify the properties of the block. Modifications are done lazily, but the passed function is composed with the
	 * current one, and as such this method can be called multiple times to perform
	 * different operations.
	 *
	 *
	 * If a different properties instance is returned, it will replace the existing one entirely.
	 *
	 * @param func
	 * The action to perform on the properties
	 * @return this [BlockBuilder]
	 */
	open fun properties(func: NonNullUnaryOperator<BlockBehaviour.Properties>): BlockBuilder<T, P> {
		propertiesCallback = propertiesCallback.andThen<BlockBehaviour.Properties>(func)
		return this
	}

	/**
	 * Replace the initial state of the block properties, without replacing or removing any modifications done via
	 * [properties].
	 *
	 * @param block
	 * The block to create the initial properties from (via
	 * [Block.Properties.ofFullCopy])
	 * @return this [BlockBuilder]
	 */
	open fun initialProperties(block: NonNullSupplier<out Block>): BlockBuilder<T, P> {
		initialProperties = NonNullSupplier { BlockBehaviour.Properties.ofFullCopy(block.get()) }
		return this
	}

	/**
	 * Create a standard [BlockItem] for this block, building it immediately, and not allowing for further
	 * configuration.
	 *
	 *
	 * The item will have no lang entry (since it would duplicate the block's)
	 *
	 * @return this [BlockBuilder]
	 * @see .item
	 */
	open fun simpleItem(): BlockBuilder<T, P> = item().build()

	/**
	 * Create a standard [BlockItem] for this block, and return the builder for it so that further customization
	 * can be done.
	 *
	 *
	 * The item will have no lang entry (since it would duplicate the block's)
	 *
	 * @return the [ItemBuilder] for the [BlockItem]
	 */
	open fun item(): ItemBuilder<BlockItem, BlockBuilder<T, P>> = item<BlockItem>(
		NonNullBiFunction { block: T, properties: Item.Properties ->
			BlockItem(
				block,
				properties,
			)
		},
	)

	/**
	 * Create a [BlockItem] for this block, which is created by the given factory, and return the builder for it
	 * so that further customization can be done.
	 *
	 *
	 * By default, the item will have no lang entry (since it would duplicate the block's) and a simple block item
	 * model.
	 *
	 * @param I
	 * The type of the item
	 * @param factory
	 * A factory for the item, which accepts the block object and properties and returns a new item
	 * @return the [ItemBuilder] for the [BlockItem]
	 */
	open fun <I : Item> item(factory: NonNullBiFunction<in T, Item.Properties, out I>): ItemBuilder<I, BlockBuilder<T, P>> {
		return owner.item<I, BlockBuilder<T, P>>(
			this,
			name,
			NonNullFunction { p: Item.Properties -> factory.apply(entry, p) },
		)
			.removeData<RegistrateLangProvider>(ProviderType.LANG)
			.model(
				NonNullSupplier {
					NonNullBiConsumer { ctx: DataGenContext<Item, I>, prov: RegistrateItemModelGenerator ->
						if (prov.hasModel(ctx.get())) {
							return@NonNullBiConsumer
						}
						val block = entry
						val model = owner.getDataProvider<RegistrateBlockModelGenerator>(ProviderType.BLOCKSTATE)
							.flatMap<ResourceLocation>(
								Function { generator: RegistrateBlockModelGenerator ->
									generator.getSingleVariantModel(
										block,
									)
								},
							)
							.orElseGet(Supplier { RegistrateBlockModelGenerator.modelLocation(block) })
						prov.createWithExistingModel(ctx.get(), model)
					}
				},
			)
	}

	/**
	 * Create a [BlockEntity] for this block, which is created by the given factory, and assigned this block as
	 * its one and only valid block.
	 *
	 * @param BE
	 * The type of the block entity
	 * @param factory
	 * A factory for the block entity
	 * @return this [BlockBuilder]
	</BE> */
	open fun <BE : BlockEntity> simpleBlockEntity(factory: BlockEntityBuilder.BlockEntityFactory<BE>): BlockBuilder<T, P> = blockEntity<BE>(factory).build()

	/**
	 * Create a [BlockEntity] for this block, which is created by the given factory, and assigned this block as
	 * its one and only valid block.
	 *
	 *
	 * The created [BlockEntityBuilder] is returned for further configuration.
	 *
	 * @param BE
	 * The type of the block entity
	 * @param factory
	 * A factory for the block entity
	 * @return the [BlockEntityBuilder]
	</BE> */
	open fun <BE : BlockEntity> blockEntity(factory: BlockEntityBuilder.BlockEntityFactory<BE>): BlockEntityBuilder<BE, BlockBuilder<T, P>> =
		owner.blockEntity<BE, BlockBuilder<T, P>>(this, name, factory).validBlock(asSupplier())

	/**
	 * Registers the client color handler for all tint indices of this block.
	 *
	 * @param colorHandler A side-safe supplier of the client color handler
	 * @return this [BlockBuilder]
	 */
	open fun color(colorHandler: NonNullSupplier<Supplier<BlockColor>>): BlockBuilder<T, P> {
		if (this.colorHandler == null) {
			unsafeRunWhenOn(Dist.CLIENT, Supplier { Runnable { this.registerBlockColor() } })
		}
		this.colorHandler = colorHandler
		return this
	}

	protected open fun registerBlockColor() {
		addModListener<RegisterColorHandlersEvent.Block>(
			owner,
			RegisterColorHandlersEvent.Block::class.java,
			Consumer { event: RegisterColorHandlersEvent.Block ->
				val colorHandler = this.colorHandler
				if (colorHandler != null) {
					event.register(colorHandler.get().get(), entry)
				}
			},
		)
	}

	/**
	 * Assign the default blockstate, which maps all states to a single model file (via
	 * [RegistrateBlockModelGenerator.createTrivialCube]). This is the default, so it is generally not
	 * necessary
	 * to call, unless for undoing previous changes.
	 *
	 * @return this [BlockBuilder]
	 */
	open fun defaultBlockstate(): BlockBuilder<T, P> = blockstate(
		NonNullSupplier {
			NonNullBiConsumer { ctx: DataGenContext<Block, T>, prov: RegistrateBlockModelGenerator ->
				prov.createTrivialCube(
					ctx.entry,
				)
			}
		},
	)

	/**
	 * Configure the blockstate/models for this block.
	 *
	 * @param cons
	 * The callback which will be invoked during data generation.
	 * @return this [BlockBuilder]
	 * @see .setData
	 */
	open fun blockstate(cons: NonNullSupplier<NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator>>): BlockBuilder<T, P> {
		val blockstate: NonNullSupplier<NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator>> =
			cons.lazy()
		return setData<RegistrateBlockModelGenerator>(
			ProviderType.BLOCKSTATE,
			NonNullBiConsumer { context: DataGenContext<Block, T>, generator: RegistrateBlockModelGenerator ->
				blockstate.get().accept(context, generator)
			},
		)
	}

	/**
	 * Assign the default translation, as specified by
	 * [RegistrateLangProvider.getAutomaticName]. This is
	 * the default, so it is generally
	 * not necessary to call, unless for undoing previous changes.
	 *
	 * @return this [BlockBuilder]
	 */
	open fun defaultLang(): BlockBuilder<T, P> = lang(NonNullFunction { obj: T -> obj.getDescriptionId() })

	/** Configures the source text and translations of this entry.  */
	open fun localization(configure: Consumer<LocalizationDeclaration.Builder>): BlockBuilder<T, P> = localization(NonNullFunction { obj: T -> obj.getDescriptionId() }, configure)

	/**
	 * Set the translation for this block.
	 *
	 * @param name
	 * A localized English name
	 * @return this [BlockBuilder]
	 */
	open fun lang(name: String): BlockBuilder<T, P> = lang(NonNullFunction { obj: T -> obj.getDescriptionId() }, name)

	/**
	 * Assign the default loot table, as specified by [RegistrateBlockLootTables.dropSelf]. This is the
	 * default, so it is generally not necessary to call, unless for
	 * undoing previous changes.
	 *
	 * @return this [BlockBuilder]
	 */
	open fun defaultLoot(): BlockBuilder<T, P> = loot(NonNullBiConsumer { obj: RegistrateBlockLootTables, block: T -> obj.dropSelf(block) })

	/**
	 * Configure the loot table for this block. This is different than most data gen callbacks as the callback does not
	 * accept a [DataGenContext], but instead a
	 * [RegistrateBlockLootTables], for creating specifically block loot tables.
	 *
	 *
	 * If the block does not have a loot table (i.e. [Block.Properties.noLootTable] is called) this action will
	 * be *skipped*.
	 *
	 * @param cons
	 * The callback which will be invoked during block loot table creation.
	 * @return this [BlockBuilder]
	 */
	open fun loot(cons: NonNullBiConsumer<RegistrateBlockLootTables, T>): BlockBuilder<T, P> = setData<RegistrateLootTableProvider>(
		ProviderType.LOOT,
		NonNullBiConsumer { ctx: DataGenContext<Block, T>, prov: RegistrateLootTableProvider ->
			prov.addLootAction<RegistrateBlockLootTables>(
				RegistrateLootTableProvider.LootType.BLOCK,
				NonNullConsumer { tb: RegistrateBlockLootTables ->
					if (ctx.entry.getLootTable() !== BuiltInLootTables.EMPTY) {
						cons.accept(tb, ctx.entry)
					}
				},
			)
		},
	)

	/**
	 * Configure the recipe(s) for this block.
	 *
	 * @param cons
	 * The callback which will be invoked during data generation.
	 * @return this [BlockBuilder]
	 * @see .setData
	 */
	open fun recipe(cons: NonNullBiConsumer<DataGenContext<Block, T>, RegistrateRecipeProvider>): BlockBuilder<T, P> = setData<RegistrateRecipeProvider>(ProviderType.RECIPE, cons)

	private var clientExtension: NonNullSupplier<Supplier<IClientBlockExtensions>>? = null

	/**
	 * Register a client extension for this block.
	 * The [IClientBlockExtensions] instance can be shared across many items.
	 *
	 * @param clientExtension
	 * The client extension to register for this block
	 * @return this [BlockBuilder]
	 */
	open fun clientExtension(clientExtension: NonNullSupplier<Supplier<IClientBlockExtensions>>): BlockBuilder<T, P> {
		if (this.clientExtension == null) {
			unsafeRunWhenOn(Dist.CLIENT, Supplier { Runnable { this.registerClientExtension() } })
		}
		this.clientExtension = clientExtension
		return this
	}

	protected open fun registerClientExtension() {
		addModListener<RegisterClientExtensionsEvent>(
			owner,
			RegisterClientExtensionsEvent::class.java,
			Consumer { e: RegisterClientExtensionsEvent ->
				val clientExtension = this.clientExtension
				if (clientExtension != null) {
					e.registerBlock(clientExtension.get().get(), entry)
				}
			},
		)
	}

	/**
	 * Assign [TagKey]`s` to this block. Multiple calls will add additional tags.
	 *
	 * @param tags
	 * The tags to assign
	 * @return this [BlockBuilder]
	 */
	@SafeVarargs
	fun tag(vararg tags: TagKey<Block>): BlockBuilder<T, P> = tag<RegistrateTagsProvider.IntrinsicTags<Block>>(ProviderType.BLOCK_TAGS, *tags)

	override fun createEntry(): T {
		var properties = this.initialProperties.get()
		properties = propertiesCallback.apply(properties)
		return factory.apply(properties)
	}

	override fun createEntryWrapper(delegate: DeferredHolder<Block, T>): RegistryEntry<Block, T> = BlockEntry<T>(owner, delegate)

	public override fun register(): BlockEntry<T> = super.register() as BlockEntry<T>

	companion object {
		/**
		 * Create a new [BlockBuilder] and configure data. Used in lieu of adding side-effects to constructor, so that
		 * alternate initialization strategies can be done in subclasses.
		 *
		 *
		 * The block will be assigned the following data:
		 *
		 *  * A default blockstate file mapping all states to one model (via [.defaultBlockstate])
		 *  * A simple cube_all model (used in the blockstate) with one texture (via [.defaultBlockstate])
		 *  * A self-dropping loot table (via [.defaultLoot])
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
		 * Factory to create the block
		 * @return A new [BlockBuilder] with reasonable default data generators.
		</P></T> */
		@JvmStatic
		fun <T : Block, P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, factory: NonNullFunction<BlockBehaviour.Properties, T>): BlockBuilder<T, P> =
			BlockBuilder<T, P>(
				owner,
				parent,
				name,
				callback,
				factory,
				NonNullSupplier { BlockBehaviour.Properties.of() },
			)
				.defaultBlockstate().defaultLoot().defaultLang()
	}
}
