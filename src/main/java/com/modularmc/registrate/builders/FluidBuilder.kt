@file:Suppress("ktlint:standard:max-line-length") // Spotless reports a line-length violation at the file header during formatting.

package com.modularmc.registrate.builders

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.builders.base.AbstractBuilder
import com.modularmc.registrate.builders.base.Builder
import com.modularmc.registrate.builders.base.BuilderCallback
import com.modularmc.registrate.internal.event.OneTimeEventReceiver.Companion.addModListener
import com.modularmc.registrate.internal.util.RegistrateDistExecutor.unsafeRunWhenOn
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.providers.DataGenContext
import com.modularmc.registrate.providers.RegistrateTagsProvider
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.providers.generators.RegistrateBlockModelGenerator
import com.modularmc.registrate.providers.generators.RegistrateItemModelGenerator
import com.modularmc.registrate.util.entry.FluidEntry
import com.modularmc.registrate.util.entry.RegistryEntry
import com.modularmc.registrate.util.nullness.*

import net.minecraft.Util
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluid
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel.Colors
import net.neoforged.neoforge.common.util.Lazy
import net.neoforged.neoforge.fluids.BaseFlowingFluid
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.NeoForgeRegistries

import com.google.common.annotations.Beta
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.util.function.Consumer
import java.util.function.Supplier
import java.util.function.ToIntFunction

open class FluidBuilder<T : BaseFlowingFluid, P : Any> : AbstractBuilder<Fluid, T, P, FluidBuilder<T, P>> {
	fun interface FluidTypeFactory {
		fun create(properties: FluidType.Properties): FluidType
	}

	fun interface FluidFactory<T> {
		fun create(properties: BaseFlowingFluid.Properties): T
	}

	@JvmRecord
	private data class FluidTextures(val still: ResourceLocation, val flowing: ResourceLocation)

	private var textures: FluidTextures? = null
	private var clientExtension: NonNullSupplier<Supplier<IClientFluidTypeExtensions>>? = null

	/**
	 * Supplies the client extension for this fluid type. The extension controls its textures, tint and overlays.
	 *
	 * @param clientExtension the side-safe supplier of the client extension
	 * @return this fluid builder
	 */
	open fun clientExtension(clientExtension: NonNullSupplier<Supplier<IClientFluidTypeExtensions>>): FluidBuilder<T, P> {
		if (this.clientExtension == null && !(registerType && textures != null)) {
			unsafeRunWhenOn(Dist.CLIENT) { Runnable { this.registerClientExtension() } }
		}
		this.clientExtension = clientExtension
		return this
	}

	/**
	 * Sets the block particle texture and the default client textures for a fluid type registered by this builder.
	 * A shared fluid type supplied to the builder uses its own client extension.
	 */
	open fun model(stillTexture: ResourceLocation, flowingTexture: ResourceLocation): FluidBuilder<T, P> {
		if (textures == null && registerType && clientExtension == null) {
			unsafeRunWhenOn(Dist.CLIENT) { Runnable { this.registerClientExtension() } }
		}
		textures = FluidTextures(stillTexture, flowingTexture)
		return this
	}

	protected open fun registerClientExtension() {
		addModListener(
			owner,
			RegisterClientExtensionsEvent::class.java,
			Consumer { event: RegisterClientExtensionsEvent ->
				val custom = clientExtension
				val fluidTextures = textures
				if (custom != null) {
					event.registerFluidType(custom.get().get(), fluidType.get())
				} else if (fluidTextures != null) {
					event.registerFluidType(
						object : IClientFluidTypeExtensions {
							override fun getStillTexture(): ResourceLocation = fluidTextures.still

							override fun getFlowingTexture(): ResourceLocation = fluidTextures.flowing
						},
						fluidType.get(),
					)
				}
			},
		)
	}

	private val sourceName: String
	private val bucketName: String

	private val fluidFactory: FluidFactory<T>

	private val fluidType: NonNullSupplier<FluidType>

	private var defaultSource: Boolean? = null
	private var defaultBlock: Boolean? = null
	private var defaultBucket: Boolean? = null

	private var typeProperties = NonNullConsumer.noop<FluidType.Properties>()

	private var fluidProperties = NonNullConsumer.noop<BaseFlowingFluid.Properties>()

	private val registerType: Boolean

	private lateinit var source: NonNullSupplier<out BaseFlowingFluid>
	private val tags = ObjectOpenHashSet<TagKey<Fluid>>()

	constructor(
		owner: AbstractRegistrate<*>,
		parent: P,
		name: String,
		callback: BuilderCallback,
		typeFactory: FluidTypeFactory,
		fluidFactory: FluidFactory<T>,
	) : super(owner, parent, "flowing_$name", callback, Registries.FLUID) {
		this.sourceName = name
		this.bucketName = name + "_bucket"
		this.fluidFactory = fluidFactory
		this.fluidType = NonNullSupplier.lazy { typeFactory.create(makeTypeProperties()) }
		this.registerType = true
	}

	constructor(
		owner: AbstractRegistrate<*>,
		parent: P,
		name: String,
		callback: BuilderCallback,
		fluidType: NonNullSupplier<FluidType>,
		fluidFactory: FluidFactory<T>,
	) : super(owner, parent, "flowing_$name", callback, Registries.FLUID) {
		this.sourceName = name
		this.bucketName = name + "_bucket"
		this.fluidFactory = fluidFactory
		this.fluidType = fluidType
		this.registerType = false // Don't register if we have a fluid from outside.
	}

	/**
	 * Modify the properties of the fluid type. Modifications are done lazily, but the passed function is composed with
	 * the current one, and as such this method can be called multiple times to perform
	 * different operations.
	 *
	 * @param cons
	 * The action to perform on the attributes
	 * @return this [FluidBuilder]
	 */
	open fun properties(cons: NonNullConsumer<FluidType.Properties>): FluidBuilder<T, P> {
		typeProperties = typeProperties.andThen(cons)
		return this
	}

	/**
	 * Modify the properties of the flowing fluid. Modifications are done lazily, but the passed function is composed
	 * with the current one, and as such this method can be called multiple times to perform
	 * different operations.
	 *
	 * @param cons
	 * The action to perform on the attributes
	 * @return this [FluidBuilder]
	 */
	open fun fluidProperties(cons: NonNullConsumer<BaseFlowingFluid.Properties>): FluidBuilder<T, P> {
		fluidProperties = fluidProperties.andThen(cons)
		return this
	}

	/**
	 * Assign the default translation, as specified by [RegistrateLangProvider.toEnglishName]. This is the
	 * default, so it is generally not necessary to call, unless for
	 * undoing previous changes.
	 *
	 * @return this [FluidBuilder]
	 */
	open fun defaultLang(): FluidBuilder<T, P> = defaultLang({ f: T -> f.fluidType.descriptionId }, sourceName)

	/** Configures the source text and translations of this entry.  */
	open fun localization(configure: Consumer<LocalizationDeclaration.Builder>): FluidBuilder<T, P> = localization({ fluid: T -> fluid.fluidType.descriptionId }, configure)

	/**
	 * Set the translation for this fluid.
	 *
	 * @param name
	 * A localized English name
	 * @return this [FluidBuilder]
	 */
	open fun lang(name: String): FluidBuilder<T, P> = lang({ f: T -> f.fluidType.descriptionId }, name)

	/**
	 * Create a standard [BaseFlowingFluid.Source] for this fluid which will be built and registered along with
	 * this fluid.
	 *
	 * @return this [FluidBuilder]
	 * @see .source
	 * @throws IllegalStateException
	 * If [source] has been called before this method
	 */
	open fun defaultSource(): FluidBuilder<T, P> {
		check(this.defaultSource == null) { "Cannot set a default source after a custom source has been created" }
		this.defaultSource = true
		return this
	}

	/**
	 * Create a [BaseFlowingFluid] for this fluid, which is created by the given factory, and which will be built
	 * and registered along with this fluid.
	 *
	 * @param factory
	 * A factory for the fluid, which accepts the properties and returns a new fluid
	 * @return this [FluidBuilder]
	 */
	open fun source(factory: NonNullFunction<BaseFlowingFluid.Properties, out BaseFlowingFluid>): FluidBuilder<T, P> {
		this.defaultSource = false
		this.source = NonNullSupplier.lazy { factory.apply(makeProperties()) }
		return this
	}

	/**
	 * Create a standard [LiquidBlock] for this fluid, building it immediately, and not allowing for further
	 * configuration.
	 *
	 * @return this [FluidBuilder]
	 * @see .block
	 * @throws IllegalStateException
	 * If [block] or [block] has been called before
	 * this method
	 */
	open fun defaultBlock(): FluidBuilder<T, P> {
		check(this.defaultBlock == null) { "Cannot set a default block after a custom block has been created" }
		this.defaultBlock = true
		return this
	}

	/**
	 * Create a standard [LiquidBlock] for this fluid, and return the builder for it so that further customization
	 * can be done.
	 *
	 * @return the [BlockBuilder] for the [LiquidBlock]
	 */
	open fun block() = block(
		NonNullBiFunction { fluid: T, properties: BlockBehaviour.Properties ->
			LiquidBlock(
				fluid,
				properties,
			)
		},
	)

	/**
	 * Create a [LiquidBlock] for this fluid, which is created by the given factory, and return the builder for it
	 * so that further customization can be done.
	 *
	 * @param B
	 * The type of the block
	 * @param factory
	 * A factory for the block, which accepts the block object and properties and returns a new block
	 * @return the [BlockBuilder] for the [LiquidBlock]
	 */
	open fun <B : LiquidBlock> block(factory: NonNullBiFunction<T, BlockBehaviour.Properties, out B>): BlockBuilder<B, FluidBuilder<T, P>> {
		check(this.defaultBlock != false) { "Only one call to block/noBlock per builder allowed" }
		this.defaultBlock = false
		val supplier: NonNullSupplier<T> = asSupplier()
		val lightLevel = Lazy.of { fluidType.get().lightLevel }
		val lightLevelInt = ToIntFunction<BlockState> { lightLevel.get() }
		val ret = owner.block(
			this,
			sourceName,
		) { p: BlockBehaviour.Properties -> factory.apply(supplier.get(), p) }
			.properties {
				BlockBehaviour.Properties.ofFullCopy(
					Blocks.WATER,
				).noLootTable()
			}
			.properties { p: BlockBehaviour.Properties -> p.lightLevel(lightLevelInt) }
			.blockstate {
				NonNullBiConsumer { ctx: DataGenContext<Block, B>, prov: RegistrateBlockModelGenerator ->
					val model = prov.models().getBuilder("block/" + ctx.name)
					val fluidTextures = textures
					if (fluidTextures != null) {
						model.texture("particle", fluidTextures.still)
					}
					prov.simpleBlock(ctx.get(), model)
				}
			}
		this.fluidProperties { it.block(ret.asSupplier()) }
		return ret
	}

	@Beta
	open fun noBlock(): FluidBuilder<T, P> {
		check(this.defaultBlock != false) { "Only one call to block/noBlock per builder allowed" }
		this.defaultBlock = false
		return this
	}

	/**
	 * Create a standard [BucketItem] for this fluid, building it immediately, and not allowing for further
	 * configuration.
	 *
	 * @return this [FluidBuilder]
	 * @see .bucket
	 * @throws IllegalStateException
	 * If [bucket] or [bucket] has been called before
	 * this method
	 */
	open fun defaultBucket(): FluidBuilder<T, P> {
		check(this.defaultBucket == null) { "Cannot set a default bucket after a custom bucket has been created" }
		defaultBucket = true
		return this
	}

	/**
	 * Create a standard [BucketItem] for this fluid, and return the builder for it so that further customization
	 * can be done.
	 *
	 * @return the [ItemBuilder] for the [BucketItem]
	 */
	open fun bucket() = bucket(
		NonNullBiFunction { content: BaseFlowingFluid, properties: Item.Properties ->
			BucketItem(
				content,
				properties,
			)
		},
	)

	/**
	 * Create a [BucketItem] for this fluid, which is created by the given factory, and return the builder for it
	 * so that further customization can be done.
	 *
	 * @param I
	 * The type of the bucket item
	 * @param factory
	 * A factory for the bucket item, which accepts the fluid object supplier and properties and returns
	 * a new item
	 * @return the [ItemBuilder] for the [BucketItem]
	 */
	open fun <I : BucketItem> bucket(factory: NonNullBiFunction<BaseFlowingFluid, Item.Properties, out I>): ItemBuilder<I, FluidBuilder<T, P>> {
		check(this.defaultBucket != false) { "Only one call to bucket/noBucket per builder allowed" }
		this.defaultBucket = false
		val source = this.source
		val ret = owner.item(
			this,
			bucketName,
		) { p: Item.Properties -> factory.apply(source.get(), p) }
			.properties { p: Item.Properties -> p.craftRemainder(Items.BUCKET).stacksTo(1) }
			.color { Supplier { Colors() } }
			.model {
				NonNullBiConsumer { ctx: DataGenContext<Item, I>, prov: RegistrateItemModelGenerator ->
					prov.generateFluidBucket(
						ctx.entry,
						source.get(),
					)
				}
			}
		this.fluidProperties { p: BaseFlowingFluid.Properties -> p.bucket(ret.asSupplier()) }
		return ret
	}

	@Beta
	open fun noBucket(): FluidBuilder<T, P> {
		check(this.defaultBucket != false) { "Only one call to bucket/noBucket per builder allowed" }
		this.defaultBucket = false
		return this
	}

	/**
	 * Assign [TagKey]`s` to this fluid and its source fluid. Multiple calls will add additional tags.
	 *
	 * @param tags
	 * The tags to assign
	 * @return this [FluidBuilder]
	 */
	@SafeVarargs
	fun tag(vararg tags: TagKey<Fluid>): FluidBuilder<T, P> {
		val ret =
			this.tag(ProviderType.FLUID_TAGS, *tags)
		if (this.tags.isEmpty()) {
			ret.owner.setDataGenerator<RegistrateTagsProvider.Intrinsic<Fluid>, Fluid>(
				ret.sourceName,
				registryKey,
				ProviderType.FLUID_TAGS,
				NonNullConsumer { prov: RegistrateTagsProvider.Intrinsic<Fluid> ->
					val source = getSource()
					for (key in this.tags) prov.tag(key).add(source)
				},
			)
		}
		this.tags.addAll(tags.asList())
		return ret
	}

	/**
	 * Remove [TagKey]`s` from this fluid and its source fluid. Multiple calls will remove additional tags.
	 *
	 * @param tags
	 * The tags to remove
	 * @return this [FluidBuilder]
	 */
	@SafeVarargs
	fun removeTag(vararg tags: TagKey<Fluid>): FluidBuilder<T, P> {
		tags.forEach { this.tags.remove(it) }
		return this.removeTag(ProviderType.FLUID_TAGS, *tags)
	}

	private fun getSource(): BaseFlowingFluid = source.get()

	private fun makeProperties(): BaseFlowingFluid.Properties {
		val ret = BaseFlowingFluid.Properties(fluidType, source, asSupplier())
		fluidProperties.accept(ret)
		return ret
	}

	private fun makeTypeProperties(): FluidType.Properties {
		val properties = FluidType.Properties.create()
		val block = owner.getOptional<Block, Block>(sourceName, Registries.BLOCK)
		this.typeProperties.accept(properties)

		// Use the sibling block's description ID for the default FluidType when it is already registered.
		if (block.isPresent && block.get().isBound) {
			properties.descriptionId(block.get().get().descriptionId)
		} else {
			properties.descriptionId(
				Util.makeDescriptionId(
					"fluid",
					ResourceLocation.fromNamespaceAndPath(owner.modid, sourceName),
				),
			)
		}

		return properties
	}

	override fun createEntry(): T = fluidFactory.create(makeProperties())

	/**
	 * {@inheritDoc}
	 *
	 *
	 * Additionally registers the source fluid and the fluid type (if constructed).
	 */
	override fun register(): FluidEntry<T> {
		if (registerType) {
			owner.simple(this, sourceName, NeoForgeRegistries.Keys.FLUID_TYPES, fluidType)
		}
		if (defaultSource == true) {
			source(NonNullFunction { properties: BaseFlowingFluid.Properties -> BaseFlowingFluid.Source(properties) })
		}
		if (defaultBlock == true) {
			block().register()
		}
		if (defaultBucket == true) {
			bucket().register()
		}

		@Suppress("UNCHECKED_CAST")
		callback.accept(sourceName, Registries.FLUID, this as Builder<Fluid, BaseFlowingFluid, *, *>, source)

		return super.register() as FluidEntry<T>
	}

	override fun createEntryWrapper(delegate: DeferredHolder<Fluid, T>): RegistryEntry<Fluid, T> = FluidEntry(owner, delegate)

	companion object {
		/**
		 * Create a new [FluidBuilder] and configure data. The created builder will use a default ([FluidType])
		 * and fluid class ([BaseFlowingFluid.Flowing]).
		 *
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
		 * @return A new [FluidBuilder] with reasonable default data generators.
		 * @see .create
		</P> */
		@JvmStatic
		fun <P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback) = create(
			owner,
			parent,
			name,
			callback,
			{ properties: FluidType.Properties -> FluidType(properties) },
			{ properties: BaseFlowingFluid.Properties -> Flowing(properties) },
		)

		/**
		 * Create a new [FluidBuilder] and configure data. The created builder will use a default fluid class
		 * ([BaseFlowingFluid.Flowing]).
		 *
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
		 * @param typeFactory
		 * A factory that creates the fluid type
		 * @return A new [FluidBuilder] with reasonable default data generators.
		 * @see .create
		</P> */
		@JvmStatic
		fun <P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, P> =
			create(owner, parent, name, callback, typeFactory, ::Flowing)

		/**
		 * Create a new [FluidBuilder] and configure data. The created builder will use a default fluid class
		 * ([BaseFlowingFluid.Flowing]).
		 *
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
		 * @param fluidType
		 * An existing and registered fluid type.
		 * @return A new [FluidBuilder] with reasonable default data generators.
		 * @see .create
		</P> */
		@JvmStatic
		fun <P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, P> =
			create(owner, parent, name, callback, FluidTypeFactory { fluidType.get() }, ::Flowing)

		/**
		 * Create a new [FluidBuilder] and configure data. The created builder will use a default ([FluidType])
		 * and fluid class ([BaseFlowingFluid.Flowing]).
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
		 * @param fluidFactory
		 * A factory that creates the flowing fluid
		 * @return A new [FluidBuilder] with reasonable default data generators.
		</P></T> */
		@JvmStatic
		fun <T : BaseFlowingFluid, P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, fluidFactory: FluidFactory<T>): FluidBuilder<T, P> =
			create(owner, parent, name, callback, ::FluidType, fluidFactory)

		/**
		 * Create a new [FluidBuilder] and configure data. Used in lieu of adding side-effects to constructor, so that
		 * alternate initialization strategies can be done in subclasses.
		 *
		 *
		 * The fluid will be assigned the following data:
		 *
		 *  * The default translation (via [.defaultLang])
		 *  * A default [source fluid][BaseFlowingFluid.Source] (via [.defaultSource])
		 *  * A default block for the fluid, with its own default blockstate and model that configure the particle texture
		 * (via [.defaultBlock])
		 *  * A default bucket item, that uses a simple generated item model with a texture of the same name as this fluid
		 * (via [.defaultBucket])
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
		 * @param typeFactory
		 * A factory that creates the fluid type
		 * @param fluidFactory
		 * A factory that creates the flowing fluid
		 * @return A new [FluidBuilder] with reasonable default data generators.
		</P></T> */
		@JvmStatic
		fun <T : BaseFlowingFluid, P : Any> create(
			owner: AbstractRegistrate<*>,
			parent: P,
			name: String,
			callback: BuilderCallback,
			typeFactory: FluidTypeFactory,
			fluidFactory: FluidFactory<T>,
		): FluidBuilder<T, P> {
			val ret =
				FluidBuilder(owner, parent, name, callback, typeFactory, fluidFactory)
					.defaultLang().defaultSource().defaultBlock().defaultBucket()
			return ret
		}

		/**
		 * Create a new [FluidBuilder] and configure data. Used in lieu of adding side-effects to constructor, so that
		 * alternate initialization strategies can be done in subclasses.
		 *
		 *
		 * The fluid will be assigned the following data:
		 *
		 *  * The default translation (via [.defaultLang])
		 *  * A default [source fluid][BaseFlowingFluid.Source] (via [.defaultSource])
		 *  * A default block for the fluid, with its own default blockstate and model that configure the particle texture
		 * (via [.defaultBlock])
		 *  * A default bucket item, that uses a simple generated item model with a texture of the same name as this fluid
		 * (via [.defaultBucket])
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
		 * @param fluidType
		 * An existing and registered fluid type
		 * @param fluidFactory
		 * A factory that creates the flowing fluid
		 * @return A new [FluidBuilder] with reasonable default data generators.
		</P></T> */
		@JvmStatic
		fun <T : BaseFlowingFluid, P : Any> create(
			owner: AbstractRegistrate<*>,
			parent: P,
			name: String,
			callback: BuilderCallback,
			fluidType: NonNullSupplier<FluidType>,
			fluidFactory: FluidFactory<T>,
		) = FluidBuilder(owner, parent, name, callback, fluidType, fluidFactory)
			.defaultLang().defaultSource().defaultBlock().defaultBucket()
	}
}
