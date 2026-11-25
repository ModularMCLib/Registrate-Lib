// 格式化 Kotlin 声明时保留与 Java 调用方对应的重载签名。
@file:Suppress("ktlint:standard:max-line-length")

package com.modularmc.registrate.builders

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.builders.base.AbstractBuilder
import com.modularmc.registrate.builders.base.BuilderCallback
import com.modularmc.registrate.internal.event.OneTimeEventReceiver.Companion.addModListener
import com.modularmc.registrate.internal.util.RegistrateDistExecutor.unsafeRunWhenOn
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.providers.DataGenContext
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.providers.loot.RegistrateEntityLootTables
import com.modularmc.registrate.providers.loot.RegistrateLootTableProvider
import com.modularmc.registrate.util.entry.EntityEntry
import com.modularmc.registrate.util.nullness.NonNullBiConsumer
import com.modularmc.registrate.util.nullness.NonNullConsumer
import com.modularmc.registrate.util.nullness.NonNullFunction
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.*
import net.minecraft.world.entity.EntityType.EntityFactory
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.SpawnEggItem
import net.minecraft.world.level.levelgen.Heightmap
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent
import net.neoforged.neoforge.registries.DeferredHolder

import java.util.function.Consumer
import java.util.function.Supplier

/**
 * 构建和注册实体类型，可配置原版实体属性、刷怪蛋和实体战利品等数据。
 *
 * @param T 实体类型
 * @param P 父级对象类型
 */
open class EntityBuilder<T : Entity, P : Any> protected constructor(
	owner: AbstractRegistrate<*>,
	parent: P,
	name: String,
	callback: BuilderCallback,
	factory: EntityFactory<T>,
	classification: MobCategory,
) : AbstractBuilder<EntityType<*>, EntityType<T>, P, EntityBuilder<T, P>>(
	owner,
	parent,
	name,
	callback,
	Registries.ENTITY_TYPE,
) {
	private val builder: NonNullSupplier<EntityType.Builder<T>> =
		NonNullSupplier { EntityType.Builder.of<T>(factory, classification) }

	private var builderCallback = NonNullConsumer.noop<EntityType.Builder<T>>()

	private var renderer: NonNullSupplier<NonNullFunction<EntityRendererProvider.Context, EntityRenderer<in T, *>>>? =
		null

	private var attributesConfigured = false
	private var spawnConfigured = false

	/**
	 * 登记实体类型属性变换。创建实体类型时按登记顺序应用各次变换。
	 *
	 * @param cons 配置原版实体类型构建器的回调
	 * @return 当前构建器
	 */
	open fun properties(cons: NonNullConsumer<EntityType.Builder<T>>): EntityBuilder<T, P> {
		builderCallback = builderCallback.andThen(cons)
		return this
	}

	/**
	 * 登记此类型的实体渲染器，供客户端渲染事件调用。
	 *
	 * @param renderer 外层供应器须可在服务端安全创建，内层工厂在客户端接收渲染上下文并创建渲染器
	 * @return 当前构建器
	 */
	open fun renderer(renderer: NonNullSupplier<NonNullFunction<EntityRendererProvider.Context, EntityRenderer<in T, *>>>): EntityBuilder<T, P> {
		if (this.renderer == null) {
			unsafeRunWhenOn(Dist.CLIENT) { Runnable { this.registerRenderer() } }
		}
		this.renderer = renderer
		return this
	}

	protected open fun registerRenderer() {
		addModListener(
			owner,
			RegisterRenderers::class.java,
			Consumer { event ->
				val provider = renderer!!.get()
				event.registerEntityRenderer(entry) { provider.apply(it) }
			},
		)
	}

	/**
	 * 为 [LivingEntity] 类型登记属性。每个构建器只能设置一次。
	 *
	 * @param attributes 用于创建属性的供应器，通常为实体类的静态方法引用
	 * @return 当前构建器
	 * @throws IllegalStateException 已登记过实体属性
	 */
	@Suppress("UNCHECKED_CAST")
	open fun attributes(attributes: Supplier<AttributeSupplier.Builder>): EntityBuilder<T, P> {
		check(!attributesConfigured) { "Cannot configure attributes more than once" }
		attributesConfigured = true
		addModListener(
			owner,
			EntityAttributeCreationEvent::class.java,
			Consumer { e: EntityAttributeCreationEvent ->
				e.put(
					entry as EntityType<LivingEntity>,
					attributes.get().build(),
				)
			},
		)
		return this
	}

	/**
	 * 为 [Mob] 类型登记生成位置规则。每个构建器只能设置一次。
	 *
	 * @param type 生成位置类型
	 * @param heightmap 用于查询候选生成位置的高度图
	 * @param predicate 判断候选位置能否生成该实体的条件
	 * @return 当前构建器
	 * @throws IllegalStateException 已登记过生成位置规则
	 */
	open fun spawnPlacement(
		type: SpawnPlacementType,
		heightmap: Heightmap.Types,
		predicate: SpawnPlacements.SpawnPredicate<T>,
		operation: RegisterSpawnPlacementsEvent.Operation,
	): EntityBuilder<T, P> {
		check(!spawnConfigured) { "Cannot configure spawn placement more than once" }
		spawnConfigured = true
		this.onRegister(
			NonNullConsumer { t ->
				addModListener(
					owner,
					RegisterSpawnPlacementsEvent::class.java,
					Consumer { e: RegisterSpawnPlacementsEvent ->
						e.register(t, type, heightmap, predicate, operation)
					},
				)
			},
		)
		return this
	}

	/** 为此实体登记刷怪蛋，可通过回调继续配置其物品构建器。 */
	@JvmOverloads
	open fun spawnEgg(consumer: NonNullConsumer<ItemBuilder<SpawnEggItem, EntityBuilder<T, P>>> = NonNullConsumer.noop()): EntityBuilder<T, P> {
		val eggBuilder = owner
			.item(this, name + "_spawn_egg", ::SpawnEggItem)
			.properties { it.spawnEgg(entry) }
			.tab(CreativeModeTabs.SPAWN_EGGS)
		consumer.accept(eggBuilder)
		return eggBuilder.build()
	}

	/**
	 * 从注册名称推导默认实体名称。可再次调用以恢复默认翻译。
	 */
	open fun defaultLang(): EntityBuilder<T, P> = lang { obj: EntityType<T> -> obj.descriptionId }

	/** 使用实体类型的描述键声明源文本及显式译文。 */
	open fun localization(configure: Consumer<LocalizationDeclaration.Builder>): EntityBuilder<T, P> = localization({ obj: EntityType<T> -> obj.descriptionId }, configure)

	/**
	 * 为此实体指定源语言名称。
	 *
	 * @param name 使用模组源语言书写的实体名称
	 * @return 当前构建器
	 */
	open fun lang(name: String): EntityBuilder<T, P> = lang({ obj: EntityType<T> -> obj.descriptionId }, name)

	/**
	 * 设置实体战利品表。回调接收 [RegistrateEntityLootTables] 和实体类型。
	 *
	 * @param cons 生成实体战利品表时执行的回调
	 * @return 当前构建器
	 */
	open fun loot(cons: NonNullBiConsumer<RegistrateEntityLootTables, EntityType<T>>) = setData(
		ProviderType.LOOT,
	) { ctx: DataGenContext<EntityType<*>, EntityType<T>>, prov: RegistrateLootTableProvider ->
		prov.addLootAction(
			RegistrateLootTableProvider.LootType.ENTITY,
		) { tb: RegistrateEntityLootTables -> cons.accept(tb, ctx.entry) }
	}

	/**
	 * 向此实体类型添加标签。多次调用会累加标签。
	 *
	 * @param tags 要添加的标签键
	 * @return 当前构建器
	 */
	@SafeVarargs
	fun tag(vararg tags: TagKey<EntityType<*>>) = tag(ProviderType.ENTITY_TAGS, *tags)

	/** 将构建回调应用到原版构建器，再使用当前注册键创建实体类型。 */
	override fun createEntry(): EntityType<T> {
		val builder: EntityType.Builder<T> = this.builder.get()
		builderCallback.accept(builder)
		return builder.build(resourceKey)
	}

	/** 用实体类型专用引用包装延迟绑定的条目。 */
	override fun createEntryWrapper(delegate: DeferredHolder<EntityType<*>, EntityType<T>>) = EntityEntry(owner, delegate)

	/** 提交实体类型注册，返回供后续生成和查询实体使用的引用。 */
	override fun register(): EntityEntry<T> = super.register() as EntityEntry<T>

	companion object {
		/**
		 * 创建 [EntityBuilder] 并配置默认翻译。子类可在此入口扩展初始化。
		 *
		 * @param T 实体类型
		 * @param P 父级对象类型
		 * @param owner 所属的 [AbstractRegistrate] 实例
		 * @param parent 构建完成后返回的父级对象
		 * @param name 条目名称
		 * @param callback 将条目提交给所属实例的回调
		 * @param factory 创建实体的工厂
		 * @param classification 实体的 [MobCategory]
		 * @return 配置好默认翻译的实体构建器
		 */
		@JvmStatic
		fun <T : Entity, P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, factory: EntityFactory<T>, classification: MobCategory) =
			EntityBuilder(owner, parent, name, callback, factory, classification)
				.defaultLang()
	}
}
