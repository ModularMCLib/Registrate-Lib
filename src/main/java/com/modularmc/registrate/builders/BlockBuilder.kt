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
import com.modularmc.registrate.providers.generators.RegistrateBlockModelGenerator
import com.modularmc.registrate.providers.generators.RegistrateItemModelGenerator
import com.modularmc.registrate.providers.generators.RegistrateRecipeProvider
import com.modularmc.registrate.providers.loot.RegistrateBlockLootTables
import com.modularmc.registrate.providers.loot.RegistrateLootTableProvider
import com.modularmc.registrate.util.entry.BlockEntry
import com.modularmc.registrate.util.nullness.*

import net.minecraft.client.color.block.BlockColor
import net.minecraft.core.registries.Registries
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
 * 构建和注册方块，可配置方块属性、创建方块物品，并生成模型、配方和战利品表。
 *
 * @param T 要构建的方块类型
 * @param P 父级对象类型
 */
open class BlockBuilder<T : Block, P : Any> protected constructor(
	owner: AbstractRegistrate<*>,
	parent: P,
	name: String,
	callback: BuilderCallback,
	private val factory: NonNullFunction<BlockBehaviour.Properties, T>,
	private var initialProperties: NonNullSupplier<BlockBehaviour.Properties>,
) : AbstractBuilder<Block, T, P, BlockBuilder<T, P>>(owner, parent, name, callback, Registries.BLOCK) {
	private var propertiesCallback =
		NonNullUnaryOperator.identity<BlockBehaviour.Properties>()

	private var colorHandler: NonNullSupplier<Supplier<BlockColor>>? = null

	/**
	 * 登记方块属性变换，构建方块时按登记顺序应用。变换返回另一份属性对象时，后续变换从新对象开始。
	 *
	 * @param func 要应用的属性变换
	 * @return 当前构建器
	 */
	open fun properties(func: NonNullUnaryOperator<BlockBehaviour.Properties>): BlockBuilder<T, P> {
		propertiesCallback = propertiesCallback.andThen<BlockBehaviour.Properties>(func)
		return this
	}

	/**
	 * 以另一方块的完整属性副本作为起点，保留已通过 [properties] 登记的变换。
	 *
	 * @param block 用于读取初始属性的方块供应器
	 * @return 当前构建器
	 */
	open fun initialProperties(block: NonNullSupplier<out Block>): BlockBuilder<T, P> {
		initialProperties = NonNullSupplier { BlockBehaviour.Properties.ofFullCopy(block.get()) }
		return this
	}

	/**
	 * 用默认配置提交此方块的 [BlockItem]。物品沿用方块的翻译键。
	 *
	 * @return 当前构建器
	 * @see item
	 */
	open fun simpleItem(): BlockBuilder<T, P> = item().build()

	/**
	 * 创建标准 [BlockItem] 的构建器，便于继续配置物品。默认使用方块的翻译键。
	 *
	 * @return 方块物品的 [ItemBuilder]
	 */
	open fun item() = item(
		NonNullBiFunction { block: T, properties: Item.Properties ->
			BlockItem(
				block,
				properties,
			)
		},
	)

	/**
	 * 使用指定工厂创建方块物品构建器。默认使用简单的方块物品模型，并沿用方块的翻译键。
	 *
	 * @param I 物品类型
	 * @param factory 接收方块和物品属性并创建物品的工厂
	 * @return 方块物品的 [ItemBuilder]
	 */
	open fun <I : Item> item(factory: NonNullBiFunction<in T, Item.Properties, out I>): ItemBuilder<I, BlockBuilder<T, P>> {
		return owner.item(
			this,
			name,
		) { p: Item.Properties -> factory.apply(entry, p) }
			.removeData(ProviderType.LANG)
			.model {
				NonNullBiConsumer { ctx: DataGenContext<Item, I>, prov: RegistrateItemModelGenerator ->
					if (prov.hasModel(ctx.get())) {
						return@NonNullBiConsumer
					}
					val block = entry
					val model = owner.getDataProvider(ProviderType.BLOCKSTATE)
						.flatMap(
							Function { generator: RegistrateBlockModelGenerator ->
								generator.getSingleVariantModel(
									block,
								)
							},
						)
						.orElseGet(Supplier { RegistrateBlockModelGenerator.modelLocation(block) })
					prov.createWithExistingModel(ctx.get(), model)
				}
			}
	}

	/**
	 * 创建并提交方块实体类型，初始有效方块为当前方块。
	 *
	 * @param BE 方块实体类型
	 * @param factory 创建方块实体的工厂
	 * @return 当前构建器
	 */
	open fun <BE : BlockEntity> simpleBlockEntity(factory: BlockEntityBuilder.BlockEntityFactory<BE>) = blockEntity(factory).build()

	/**
	 * 创建方块实体构建器，初始有效方块为当前方块。可在返回的构建器上继续添加有效方块及其他配置。
	 *
	 * @param BE 方块实体类型
	 * @param factory 创建方块实体的工厂
	 * @return 方块实体的 [BlockEntityBuilder]
	 */
	open fun <BE : BlockEntity> blockEntity(factory: BlockEntityBuilder.BlockEntityFactory<BE>) = owner.blockEntity(this, name, factory).validBlock(asSupplier())

	/**
	 * Registers the client color handler for all tint indices of this block.
	 *
	 * @param colorHandler A side-safe supplier of the client color handler
	 * @return this [BlockBuilder]
	 */
	open fun color(colorHandler: NonNullSupplier<Supplier<BlockColor>>): BlockBuilder<T, P> {
		if (this.colorHandler == null) {
			unsafeRunWhenOn(Dist.CLIENT) { Runnable { this.registerBlockColor() } }
		}
		this.colorHandler = colorHandler
		return this
	}

	protected open fun registerBlockColor() {
		addModListener(
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
	 * 使用 [RegistrateBlockModelGenerator.createTrivialCube] 生成默认方块状态，所有状态共用一个立方体模型。
	 * 可再次调用以替换之前设置的生成方式。
	 *
	 * @return 当前构建器
	 */
	open fun defaultBlockstate(): BlockBuilder<T, P> = blockstate {
		NonNullBiConsumer { ctx: DataGenContext<Block, T>, prov: RegistrateBlockModelGenerator ->
			prov.createTrivialCube(
				ctx.entry,
			)
		}
	}

	/**
	 * 设置此方块的方块状态和模型生成回调。
	 *
	 * @param cons 提供数据生成回调的供应器
	 * @return 当前构建器
	 * @see setData
	 */
	open fun blockstate(cons: NonNullSupplier<NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator>>): BlockBuilder<T, P> {
		val blockstate: NonNullSupplier<NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator>> =
			cons.lazy()
		return setData(
			ProviderType.BLOCKSTATE,
		) { context: DataGenContext<Block, T>, generator: RegistrateBlockModelGenerator ->
			blockstate.get().accept(context, generator)
		}
	}

	/**
	 * 用 [RegistrateLangProvider.getAutomaticName] 从注册名称推导默认翻译，可用于恢复默认生成方式。
	 *
	 * @return 当前构建器
	 */
	open fun defaultLang(): BlockBuilder<T, P> = lang { obj: T -> obj.descriptionId }

	/** 使用方块的描述键声明源文本及显式译文。 */
	open fun localization(configure: Consumer<LocalizationDeclaration.Builder>): BlockBuilder<T, P> = localization({ obj: T -> obj.descriptionId }, configure)

	/**
	 * 为此方块指定源语言名称。
	 *
	 * @param name 使用模组源语言书写的方块名称
	 * @return 当前构建器
	 */
	open fun lang(name: String): BlockBuilder<T, P> = lang({ obj: T -> obj.descriptionId }, name)

	/**
	 * 恢复默认战利品表。方块被破坏时按 [RegistrateBlockLootTables.dropSelf] 的规则掉落自身。
	 *
	 * @return 当前构建器
	 */
	open fun defaultLoot(): BlockBuilder<T, P> = loot { obj: RegistrateBlockLootTables, block: T -> obj.dropSelf(block) }

	/**
	 * 配置方块战利品表。回调接收专用的 [RegistrateBlockLootTables] 和方块对象。
	 * 方块通过 [Block.Properties.noLootTable] 禁用战利品表时，跳过该回调。
	 *
	 * @param cons 生成方块战利品表时执行的回调
	 * @return 当前构建器
	 */
	open fun loot(cons: NonNullBiConsumer<RegistrateBlockLootTables, T>) = setData(
		ProviderType.LOOT,
	) { ctx: DataGenContext<Block, T>, prov: RegistrateLootTableProvider ->
		prov.addLootAction(
			RegistrateLootTableProvider.LootType.BLOCK,
		) { tb: RegistrateBlockLootTables ->
			if (ctx.entry.lootTable !== BuiltInLootTables.EMPTY) {
				cons.accept(tb, ctx.entry)
			}
		}
	}

	/**
	 * 设置此方块的配方生成回调。
	 *
	 * @param cons 数据生成时执行的回调
	 * @return 当前构建器
	 * @see setData
	 */
	open fun recipe(cons: NonNullBiConsumer<DataGenContext<Block, T>, RegistrateRecipeProvider>) = setData(ProviderType.RECIPE, cons)

	private var clientExtension: NonNullSupplier<Supplier<IClientBlockExtensions>>? = null

	/**
	 * 登记方块客户端扩展。同一个 [IClientBlockExtensions] 实例可供多个方块共用。
	 *
	 * @param clientExtension 外层供应器须可在服务端安全创建，内层供应器在客户端取得扩展
	 * @return 当前构建器
	 */
	open fun clientExtension(clientExtension: NonNullSupplier<Supplier<IClientBlockExtensions>>): BlockBuilder<T, P> {
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
					e.registerBlock(clientExtension.get().get(), entry)
				}
			},
		)
	}

	/**
	 * 向此方块添加标签。多次调用会累加标签。
	 *
	 * @param tags 要添加的标签键
	 * @return 当前构建器
	 */
	@SafeVarargs
	fun tag(vararg tags: TagKey<Block>) = tag(ProviderType.BLOCK_TAGS, *tags)

	/** 应用已登记的属性变换，写入注册键，再由工厂创建方块。 */
	override fun createEntry(): T {
		var properties = this.initialProperties.get()
		properties = propertiesCallback.apply(properties)
		return factory.apply(properties)
	}

	/** 用方块专用引用包装延迟绑定的条目，便于获取关联方块物品。 */
	override fun createEntryWrapper(delegate: DeferredHolder<Block, T>) = BlockEntry(owner, delegate)

	/** 提交方块注册，返回可保存到静态字段的方块引用。 */
	override fun register(): BlockEntry<T> = super.register() as BlockEntry<T>

	companion object {
		/**
		 * 创建带默认数据配置的 [BlockBuilder]，集中设置可由子类替换的生成规则。
		 * 默认让所有方块状态共用单纹理立方体模型，生成掉落自身的战利品表和默认名称。
		 *
		 * @param T 方块类型
		 * @param P 父级对象类型
		 * @param owner 所属的 [AbstractRegistrate] 实例
		 * @param parent 构建完成后返回的父级对象
		 * @param name 条目名称
		 * @param callback 将条目提交给所属实例的回调
		 * @param factory 接收属性并创建方块的工厂
		 * @return 配置好默认数据生成规则的构建器
		 */
		@JvmStatic
		fun <T : Block, P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, factory: NonNullFunction<BlockBehaviour.Properties, T>) = BlockBuilder(
			owner,
			parent,
			name,
			callback,
			factory,
		) { BlockBehaviour.Properties.of() }
			.defaultBlockstate().defaultLoot().defaultLang()
	}
}
