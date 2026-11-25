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
 * 构建和注册物品，可配置物品属性、模型和配方等数据。
 *
 * @param T 物品类型
 * @param P 父级对象类型
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
	 * 登记物品属性变换，构建物品时按登记顺序应用。变换返回另一份属性对象时，后续变换从新对象开始。
	 *
	 * @param func 要应用的属性变换
	 * @return 当前构建器
	 */
	open fun properties(func: NonNullUnaryOperator<Item.Properties>): ItemBuilder<T, P> {
		propertiesCallback = propertiesCallback.andThen(func)
		return this
	}

	/**
	 * 设置物品属性的起点，保留已通过 [properties] 登记的变换。
	 *
	 * @param properties 创建初始属性的供应器
	 * @return 当前构建器
	 */
	open fun initialProperties(properties: NonNullSupplier<Item.Properties>): ItemBuilder<T, P> {
		initialProperties = properties
		return this
	}

	/** 将物品加入指定创造模式标签页。可选的回调控制该物品在标签页中的呈现。 */
	@JvmOverloads
	open fun tab(
		tab: ResourceKey<CreativeModeTab>,
		modifier: NonNullBiConsumer<DataGenContext<Item, T>, CreativeModeTabModifier> = NonNullBiConsumer { item: DataGenContext<Item, T>, modifier: CreativeModeTabModifier ->
			modifier.accept(item)
		},
	): ItemBuilder<T, P> {
		creativeModeTabs[tab] = modifier // 同一个标签页只保存一个回调。
		// 重复登记会替换之前的回调。
		// 如果需要组合回调，由调用方组合后登记。
		return this
	}

	/**
	 * 移除该物品对此创造模式标签页登记的回调。
	 *
	 * @param tab 要移除回调的标签页键
	 * @return 当前构建器
	 */
	open fun removeTab(tab: ResourceKey<CreativeModeTab>): ItemBuilder<T, P> {
		creativeModeTabs.remove(tab)
		return this
	}

	/**
	 * 使用与注册名称同名的单张纹理生成默认物品模型。
	 *
	 * @return 当前构建器
	 */
	open fun defaultModel(): ItemBuilder<T, P> = model {
		NonNullBiConsumer { ctx, provider ->
			provider.generateFlatItem(ctx.get(), provider.modItemTexture(ctx.name))
		}
	}

	/**
	 * 生成默认物品模型，并为模型各层配置着色来源。
	 *
	 * @param tints 按模型图层顺序排列的着色来源
	 * @return 当前构建器
	 */
	open fun defaultModel(vararg tints: ItemTintSource): ItemBuilder<T, P> = model {
		NonNullBiConsumer { ctx, provider ->
			val item = ctx.get()
			provider.generateFlatItem(item, ModelTemplates.FLAT_ITEM, TextureMapping.layer0(item), *tints)
		}
	}

	/**
	 * 设置此物品的模型生成回调。
	 *
	 * @param cons 提供数据生成回调的供应器
	 * @return 当前构建器
	 * @see setData
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
	 * 用 [RegistrateLangProvider.getAutomaticName] 从注册名称推导默认翻译，可用于恢复默认生成方式。
	 *
	 * @return 当前构建器
	 */
	open fun defaultLang(): ItemBuilder<T, P> = lang { obj: T -> obj.descriptionId }

	/** 使用物品的描述键声明源文本及显式译文。 */
	open fun localization(configure: Consumer<LocalizationDeclaration.Builder>): ItemBuilder<T, P> = localization({ obj: T -> obj.descriptionId }, configure)

	/**
	 * 为此物品指定源语言名称。
	 *
	 * @param name 使用模组源语言书写的物品名称
	 * @return 当前构建器
	 */
	open fun lang(name: String): ItemBuilder<T, P> = lang({ obj: T -> obj.descriptionId }, name)

	/**
	 * 设置此物品的配方生成回调。
	 *
	 * @param cons 数据生成时执行的回调
	 * @return 当前构建器
	 * @see setData
	 */
	open fun recipe(cons: NonNullBiConsumer<DataGenContext<Item, T>, RegistrateRecipeProvider>) = setData(ProviderType.RECIPE, cons)

	/**
	 * 将物品登记为熔炉燃料。
	 *
	 * @param tick 该物品在熔炉中燃烧的游戏刻数
	 */
	open fun burnTime(tick: Int): ItemBuilder<T, P> = dataMap(NeoForgeDataMaps.FURNACE_FUELS, FurnaceFuel(tick))

	/**
	 * 登记物品的堆肥成功概率。
	 *
	 * @param chance 投入一个该物品时堆肥桶增加一层的概率
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
				creativeModeTabs.clear() // 这个注册回调只需执行一次，用完即清空待处理的扩展。
			},
		)
	}

	/**
	 * 登记物品客户端扩展。同一个 [IClientItemExtensions] 实例可供多个物品共用。
	 *
	 * @param clientExtension 外层供应器须可在服务端安全创建，内层供应器在客户端取得扩展
	 * @return 当前构建器
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
	 * 向物品添加标签。多次调用会累加标签。
	 *
	 * @param tags 要添加的物品标签
	 * @return 当前构建器
	 */
	@SafeVarargs
	fun tag(vararg tags: TagKey<Item>) = tag(ProviderType.ITEM_TAGS, *tags)

	/** 应用物品属性变换，写入注册键，再由工厂创建物品。 */
	override fun createEntry(): T {
		var properties = this.initialProperties.get()
		properties = propertiesCallback.apply(properties)
		return factory.apply(properties.setId(resourceKey))
	}

	/** 用物品专用引用包装延迟绑定的条目。 */
	override fun createEntryWrapper(delegate: DeferredHolder<Item, T>): RegistryEntry<Item, T> = ItemEntry(owner, delegate)

	/** 提交物品注册，返回可用于构造物品堆叠的物品引用。 */
	override fun register(): ItemEntry<T> = super.register() as ItemEntry<T>

	companion object {
		/**
		 * 创建 [ItemBuilder]，并配置使用单张纹理的默认物品模型及默认翻译。子类可在此入口扩展初始化。
		 *
		 * @param T 物品类型
		 * @param P 父级对象类型
		 * @param owner 所属的 [AbstractRegistrate] 实例
		 * @param parent 构建完成后返回的父级对象
		 * @param name 条目名称
		 * @param callback 将条目提交给所属实例的回调
		 * @param factory 接收属性并创建物品的工厂
		 * @return 配置好默认模型和翻译的物品构建器
		 */
		@JvmStatic
		fun <T : Item, P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, factory: NonNullFunction<Item.Properties, T>) =
			ItemBuilder(owner, parent, name, callback, factory)
				.defaultModel().defaultLang()
	}
}
