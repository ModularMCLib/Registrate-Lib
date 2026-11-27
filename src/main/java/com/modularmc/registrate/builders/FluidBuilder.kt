@file:Suppress("ktlint:standard:max-line-length") // Spotless 格式化时会在文件头报告行宽超限。

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
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.RenderType
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
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
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

/** 配置流体类型、源流体和流动流体的注册，并可附带方块、桶及客户端模型。 */
open class FluidBuilder<T : BaseFlowingFluid, P : Any> : AbstractBuilder<Fluid, T, P, FluidBuilder<T, P>> {
	/** 由模组提供，用于在注册阶段按配置创建一个新的 NeoForge 流体类型。 */
	fun interface FluidTypeFactory {
		/** 以配置完成的 [properties] 创建流体类型，返回供源流体与流动流体共享的实例。 */
		fun create(properties: FluidType.Properties): FluidType
	}

	/** 由模组提供，用于在流体注册阶段创建具体的流动流体。 */
	fun interface FluidFactory<T> {
		/** 用已配置的 [properties] 创建流动流体。属性中引用的附属条目由注册流程延迟取得。 */
		fun create(properties: BaseFlowingFluid.Properties): T
	}

	@JvmRecord
	private data class FluidTextures(val still: ResourceLocation, val flowing: ResourceLocation)

	private var textures: FluidTextures? = null
	private var clientExtension: NonNullSupplier<Supplier<IClientFluidTypeExtensions>>? = null
	private var renderLayer: Supplier<Supplier<RenderType>>? = null

	/**
	 * Supplies the client extension for this fluid type. The extension controls its textures, tint and overlays.
	 *
	 * @param clientExtension 外层供应器须可在服务端安全创建，内层供应器在客户端读取扩展
	 * @return 当前构建器
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

	/**
	 * 设置源流体和流动流体共用的区块渲染层。重复调用时采用最后一次设置。
	 *
	 * @param layer 双层供应器，仅在客户端求值
	 * @return 当前构建器
	 * @throws IllegalArgumentException 客户端提供的类型不是区块渲染层
	 */
	open fun renderType(layer: Supplier<Supplier<RenderType>>): FluidBuilder<T, P> {
		unsafeRunWhenOn(Dist.CLIENT) {
			Runnable {
				require(layer.get().get() in RenderType.chunkBufferLayers()) { "Invalid fluid render layer for ${owner.modid}:$sourceName" }
			}
		}
		if (renderLayer == null) {
			onRegister(::registerRenderType)
		}
		renderLayer = layer
		return this
	}

	/** 流动流体注册完成后，为客户端设置阶段安排两种流体的渲染层登记。 */
	protected open fun registerRenderType(fluid: T) {
		unsafeRunWhenOn(Dist.CLIENT) {
			Runnable {
				addModListener(owner, FMLClientSetupEvent::class.java) { event ->
					event.enqueueWork {
						val layer = checkNotNull(renderLayer).get().get()
						ItemBlockRenderTypes.setRenderLayer(fluid, layer)
						ItemBlockRenderTypes.setRenderLayer(getSource(), layer)
					}
				}
			}
		}
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

	/** 创建会同时注册新流体类型的流体构建器。实际类型由 [typeFactory] 延迟创建。 */
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

	/** 创建共享已有流体类型的构建器。引用由 [fluidType] 提供，类型注册由其所有者负责。 */
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
		this.registerType = false // 外部提供的流体类型由其所有者登记。
	}

	/**
	 * 登记流体类型属性变换，创建流体类型时按登记顺序应用。
	 *
	 * @param cons 配置流体类型属性的回调
	 * @return 当前构建器
	 */
	open fun properties(cons: NonNullConsumer<FluidType.Properties>): FluidBuilder<T, P> {
		typeProperties = typeProperties.andThen(cons)
		return this
	}

	/**
	 * 登记流动流体的属性变换。创建流体时按登记顺序应用。
	 *
	 * @param cons 配置流动流体属性的回调
	 * @return 当前构建器
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

	/** 使用流体类型的描述键声明源文本及显式译文。 */
	open fun localization(configure: Consumer<LocalizationDeclaration.Builder>): FluidBuilder<T, P> = localization({ fluid: T -> fluid.fluidType.descriptionId }, configure)

	/**
	 * 为此流体指定源语言名称。
	 *
	 * @param name 使用模组源语言书写的流体名称
	 * @return 当前构建器
	 */
	open fun lang(name: String): FluidBuilder<T, P> = lang({ f: T -> f.fluidType.descriptionId }, name)

	/**
	 * 为此流体配置标准 [BaseFlowingFluid.Source]，随流动流体一起构建和登记。
	 *
	 * @return 当前构建器
	 * @see source
	 * @throws IllegalStateException 已配置过源流体
	 */
	open fun defaultSource(): FluidBuilder<T, P> {
		check(this.defaultSource == null) { "Cannot set a default source after a custom source has been created" }
		this.defaultSource = true
		return this
	}

	/**
	 * 使用指定工厂创建源流体，随流动流体一并登记。
	 *
	 * @param factory 接收流体属性并创建源流体的工厂
	 * @return 当前构建器
	 */
	open fun source(factory: NonNullFunction<BaseFlowingFluid.Properties, out BaseFlowingFluid>): FluidBuilder<T, P> {
		this.defaultSource = false
		this.source = NonNullSupplier.lazy { factory.apply(makeProperties()) }
		return this
	}

	/**
	 * 为流体启用标准 [LiquidBlock]。需要自定义属性时可用 [block] 取得构建器。
	 *
	 * @return 当前构建器
	 * @throws IllegalStateException 已设置过液体方块或禁用液体方块
	 */
	open fun defaultBlock(): FluidBuilder<T, P> {
		check(this.defaultBlock == null) { "Cannot set a default block after a custom block has been created" }
		this.defaultBlock = true
		return this
	}

	/**
	 * 创建标准 [LiquidBlock] 的构建器，便于继续配置液体方块。
	 *
	 * @return 液体方块的 [BlockBuilder]
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
	 * 使用指定工厂创建液体方块构建器。
	 *
	 * @param B 液体方块类型
	 * @param factory 接收源流体和方块属性并创建液体方块的工厂
	 * @return 液体方块的 [BlockBuilder]
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

	/** 关闭默认流体方块。与其他方块配置入口互斥，重复关闭会抛出 [IllegalStateException]。 */
	@Beta
	open fun noBlock(): FluidBuilder<T, P> {
		check(this.defaultBlock != false) { "Only one call to block/noBlock per builder allowed" }
		this.defaultBlock = false
		return this
	}

	/**
	 * 为流体启用标准 [BucketItem]。需要自定义物品时可用 [bucket] 取得构建器。
	 *
	 * @return 当前构建器
	 * @throws IllegalStateException 已设置过桶物品或禁用桶物品
	 */
	open fun defaultBucket(): FluidBuilder<T, P> {
		check(this.defaultBucket == null) { "Cannot set a default bucket after a custom bucket has been created" }
		defaultBucket = true
		return this
	}

	/**
	 * 创建标准 [BucketItem] 的构建器，便于继续配置桶物品。
	 *
	 * @return 桶物品的 [ItemBuilder]
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
	 * 使用指定工厂创建流体桶构建器。
	 *
	 * @param I 桶物品类型
	 * @param factory 接收流体和物品属性并创建桶物品的工厂
	 * @return 桶物品的 [ItemBuilder]
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

	/** 关闭默认桶物品。与其他桶配置入口互斥，重复关闭会抛出 [IllegalStateException]。 */
	@Beta
	open fun noBucket(): FluidBuilder<T, P> {
		check(this.defaultBucket != false) { "Only one call to bucket/noBucket per builder allowed" }
		this.defaultBucket = false
		return this
	}

	/**
	 * 向流动流体和源流体添加标签。多次调用会累加标签。
	 *
	 * @param tags 要添加的流体标签
	 * @return 当前构建器
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
	 * 从流动流体和源流体的生成配置移除指定标签。
	 *
	 * @param tags 要移除的流体标签
	 * @return 当前构建器
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

		// 关联方块已注册时，默认流体类型沿用该方块的描述键。
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

	/** 按已配置的属性调用流体工厂，创建流动流体实例。 */
	override fun createEntry(): T = fluidFactory.create(makeProperties())

	/**
	 * 提交流动流体时一并登记源流体，以及由当前构建器创建的流体类型。
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

	/** 将流动流体包装为流体专用引用，供查询同组的源流体、方块和桶。 */
	override fun createEntryWrapper(delegate: DeferredHolder<Fluid, T>): RegistryEntry<Fluid, T> = FluidEntry(owner, delegate)

	companion object {
		/**
		 * 创建使用默认 [FluidType] 和 [BaseFlowingFluid.Flowing] 的构建器。
		 *
		 * @param P 父级对象类型
		 * @param owner 所属的 [AbstractRegistrate] 实例
		 * @param parent 构建完成后返回的父级对象
		 * @param name 条目名称
		 * @param callback 将条目提交给所属实例的回调
		 * @return 已配置默认数据的 [FluidBuilder]
		 */
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
		 * 使用指定的流体类型工厂，流动流体采用 [BaseFlowingFluid.Flowing]。
		 *
		 * @param P 父级对象类型
		 * @param owner 所属的 [AbstractRegistrate] 实例
		 * @param parent 构建完成后返回的父级对象
		 * @param name 条目名称
		 * @param callback 将条目提交给所属实例的回调
		 * @param typeFactory 创建流体类型的工厂
		 * @return 已配置默认数据的 [FluidBuilder]
		 */
		@JvmStatic
		fun <P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, typeFactory: FluidTypeFactory): FluidBuilder<Flowing, P> =
			create(owner, parent, name, callback, typeFactory, ::Flowing)

		/**
		 * 复用已注册的流体类型，流动流体采用 [BaseFlowingFluid.Flowing]。
		 *
		 * @param P 父级对象类型
		 * @param owner 所属的 [AbstractRegistrate] 实例
		 * @param parent 构建完成后返回的父级对象
		 * @param name 条目名称
		 * @param callback 将条目提交给所属实例的回调
		 * @param fluidType 已注册的流体类型
		 * @return 已配置默认数据的 [FluidBuilder]
		 */
		@JvmStatic
		fun <P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, fluidType: NonNullSupplier<FluidType>): FluidBuilder<Flowing, P> =
			create(owner, parent, name, callback, FluidTypeFactory { fluidType.get() }, ::Flowing)

		/**
		 * 使用默认 [FluidType]，由调用方提供流动流体的工厂。
		 *
		 * @param T 流体类型
		 * @param P 父级对象类型
		 * @param owner 所属的 [AbstractRegistrate] 实例
		 * @param parent 构建完成后返回的父级对象
		 * @param name 条目名称
		 * @param callback 将条目提交给所属实例的回调
		 * @param fluidFactory 创建流动流体的工厂
		 * @return 已配置默认数据的 [FluidBuilder]
		 */
		@JvmStatic
		fun <T : BaseFlowingFluid, P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, fluidFactory: FluidFactory<T>): FluidBuilder<T, P> =
			create(owner, parent, name, callback, ::FluidType, fluidFactory)

		/**
		 * 创建 [FluidBuilder] 并安装默认配置，便于子类自定义初始化过程。
		 * 默认生成源流体及其名称、带粒子纹理的液体方块模型，以及使用同名纹理的桶物品模型。
		 *
		 * @param T 流体类型
		 * @param P 父级对象类型
		 * @param owner 所属的 [AbstractRegistrate] 实例
		 * @param parent 构建完成后返回的父级对象
		 * @param name 条目名称
		 * @param callback 将条目提交给所属实例的回调
		 * @param typeFactory 创建流体类型的工厂
		 * @param fluidFactory 创建流动流体的工厂
		 * @return 已配置默认数据的 [FluidBuilder]
		 */
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
		 * 创建 [FluidBuilder] 并安装默认配置，便于子类自定义初始化过程。
		 * 默认生成源流体及其名称、带粒子纹理的液体方块模型，以及使用同名纹理的桶物品模型。
		 *
		 * @param T 流体类型
		 * @param P 父级对象类型
		 * @param owner 所属的 [AbstractRegistrate] 实例
		 * @param parent 构建完成后返回的父级对象
		 * @param name 条目名称
		 * @param callback 将条目提交给所属实例的回调
		 * @param fluidType 已注册并由当前流体复用的类型
		 * @param fluidFactory 创建流动流体的工厂
		 * @return 已配置默认数据的 [FluidBuilder]
		 */
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
