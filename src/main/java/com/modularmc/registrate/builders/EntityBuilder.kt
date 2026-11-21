// Keep the Java overload signatures while formatting the Kotlin declarations.
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
import com.modularmc.registrate.providers.generators.RegistrateItemModelGenerator
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
import net.minecraft.world.item.Item
import net.minecraft.world.item.SpawnEggItem
import net.minecraft.world.level.levelgen.Heightmap
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers
import net.neoforged.neoforge.common.DeferredSpawnEggItem
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent
import net.neoforged.neoforge.registries.DeferredHolder

import java.util.function.Consumer
import java.util.function.Supplier

/**
 * A builder for entities, allows for customization of the [EntityType.Builder], easy creation of spawn egg items,
 * and configuration of data associated with entities (loot tables, etc.).
 *
 * @param T
 * The type of entity being built
 * @param P
 * Parent object type
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

	private var builderCallback = NonNullConsumer { `$`: EntityType.Builder<T> -> }

	private var renderer: NonNullSupplier<NonNullFunction<EntityRendererProvider.Context, EntityRenderer<in T>>>? =
		null

	private var attributesConfigured = false
	private var spawnConfigured = false

	/**
	 * Modify the properties of the entity. Modifications are done lazily, but the passed function is composed with the
	 * current one, and as such this method can be called multiple times to perform
	 * different operations.
	 *
	 * @param cons
	 * The action to perform on the properties
	 * @return this [EntityBuilder]
	 */
	open fun properties(cons: NonNullConsumer<EntityType.Builder<T>>): EntityBuilder<T, P> {
		builderCallback = builderCallback.andThen(cons)
		return this
	}

	/**
	 * Register an [EntityRenderer] for this entity.
	 *
	 *
	 *
	 * @param renderer
	 * A (server safe) supplier to an [EntityRendererProvider] that will provide this entity's
	 * renderer
	 * @return this [EntityBuilder]
	 */
	open fun renderer(renderer: NonNullSupplier<NonNullFunction<EntityRendererProvider.Context, EntityRenderer<in T>>>): EntityBuilder<T, P> {
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
			Consumer { evt: RegisterRenderers ->
				val renderer: NonNullSupplier<NonNullFunction<EntityRendererProvider.Context, EntityRenderer<in T>>>? =
					this.renderer
				if (renderer != null) {
					try {
						val provider = renderer.get()
						evt.registerEntityRenderer(entry) { context -> provider.apply(context) }
					} catch (e: Exception) {
						throw IllegalStateException("Failed to register renderer for Entity " + get().id, e)
					}
				}
			},
		)
	}

	/**
	 * Register a attributes for this entity. The entity must extend [LivingEntity].
	 *
	 *
	 * Cannot be called more than once per builder.
	 *
	 * @param attributes
	 * A supplier to the attributes for this entity, usually of the form
	 * `EntityClass::createAttributes`
	 * @return this [EntityBuilder]
	 * @throws IllegalStateException
	 * When called more than once
	 */
	open fun attributes(attributes: Supplier<AttributeSupplier.Builder>): EntityBuilder<T, P> {
		check(!attributesConfigured) { "Cannot configure attributes more than once" }
		attributesConfigured = true
		addModListener(
			owner,
			EntityAttributeCreationEvent::class.java,
			Consumer { e: EntityAttributeCreationEvent ->
				e.put(
					entry as EntityType<LivingEntity?>,
					attributes.get().build(),
				)
			},
		)
		return this
	}

	/**
	 * Register a spawn placement for this entity. The entity must extend [Mob] and allow construction with a
	 * `null` [net.minecraft.world.level.Level].
	 *
	 *
	 * Cannot be called more than once per builder.
	 *
	 * @param type
	 * The type of placement to use
	 * @param heightmap
	 * Which heightmap to use to choose placement locations
	 * @param predicate
	 * A predicate to check spawn locations for validity
	 * @return this [EntityBuilder]
	 * @throws IllegalStateException
	 * When called more than once
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
			NonNullConsumer { t: EntityType<T>? ->
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

	/** Registers a spawn egg using the given customization callback and standard model. */
	@JvmOverloads
	open fun spawnEgg(consumer: NonNullConsumer<ItemBuilder<SpawnEggItem, EntityBuilder<T, P>>> = NonNullConsumer.noop()): EntityBuilder<T, P> = spawnEgg(0xFFFFFF, 0xFFFFFF, consumer)

	/**
	 * Registers a spawn egg with the given base and highlight colors and configures it before registration.
	 */
	open fun spawnEgg(baseColor: Int, highlightColor: Int, consumer: NonNullConsumer<ItemBuilder<SpawnEggItem, EntityBuilder<T, P>>>): EntityBuilder<T, P> {
		val entityType: NonNullSupplier<EntityType<T>> = asSupplier()
		// NeoForge's deferred egg API accepts a mob factory. Registrate resolves the type after the item registry runs.
		val mobType = entityType as Supplier<*> as Supplier<out EntityType<out Mob>>
		val eggBuilder = owner
			.item<SpawnEggItem, EntityBuilder<T, P>>(
				this,
				name + "_spawn_egg",
			) { properties: Item.Properties ->
				DeferredSpawnEggItem(
					mobType,
					baseColor,
					highlightColor,
					properties,
				)
			}
			.model {
				NonNullBiConsumer { ctx: DataGenContext<Item, SpawnEggItem>, prov: RegistrateItemModelGenerator ->
					prov.createWithExistingModel(
						ctx.get(),
						prov.mcLoc("item/template_spawn_egg"),
					)
				}
			}
			.tab(CreativeModeTabs.SPAWN_EGGS)
		consumer.accept(eggBuilder)
		return eggBuilder.build()
	}

	/**
	 * Assign the default translation, as specified by
	 * [com.modularmc.registrate.providers.RegistrateLangProvider.getAutomaticName]. This is
	 * the default, so it is generally
	 * not necessary to call, unless for undoing previous changes.
	 *
	 * @return this [EntityBuilder]
	 */
	open fun defaultLang(): EntityBuilder<T, P> = lang { obj: EntityType<T> -> obj.descriptionId }

	/** Configures the source text and translations of this entry.  */
	open fun localization(configure: Consumer<LocalizationDeclaration.Builder>): EntityBuilder<T, P> = localization({ obj: EntityType<T> -> obj.descriptionId }, configure)

	/**
	 * Set the translation for this entity.
	 *
	 * @param name
	 * A localized English name
	 * @return this [EntityBuilder]
	 */
	open fun lang(name: String): EntityBuilder<T, P> = lang({ obj: EntityType<T> -> obj.descriptionId }, name)

	/**
	 * Configure the loot table for this entity. This is different than most data gen callbacks as the callback does not
	 * accept a [DataGenContext], but instead a
	 * [RegistrateEntityLootTables], for creating specifically entity loot tables.
	 *
	 * @param cons
	 * The callback which will be invoked during entity loot table creation.
	 * @return this [EntityBuilder]
	 */
	open fun loot(cons: NonNullBiConsumer<RegistrateEntityLootTables, EntityType<T>>) = setData(
		ProviderType.LOOT,
	) { ctx: DataGenContext<EntityType<*>, EntityType<T>>, prov: RegistrateLootTableProvider ->
		prov.addLootAction(
			RegistrateLootTableProvider.LootType.ENTITY,
		) { tb: RegistrateEntityLootTables -> cons.accept(tb, ctx.entry) }
	}

	/**
	 * Assign [TagKey]`s` to this entity. Multiple calls will add additional tags.
	 *
	 * @param tags
	 * The tags to assign
	 * @return this [EntityBuilder]
	 */
	@SafeVarargs
	fun tag(vararg tags: TagKey<EntityType<*>>) = tag(ProviderType.ENTITY_TAGS, *tags)

	override fun createEntry(): EntityType<T> {
		val builder: EntityType.Builder<T> = this.builder.get()
		builderCallback.accept(builder)
		return builder.build(resourceKey.location().toString())
	}

	override fun createEntryWrapper(delegate: DeferredHolder<EntityType<*>, EntityType<T>>) = EntityEntry(owner, delegate)

	override fun register(): EntityEntry<T> = super.register() as EntityEntry<T>

	companion object {
		/**
		 * Create a new [EntityBuilder] and configure data. Used in lieu of adding side-effects to constructor, so
		 * that alternate initialization strategies can be done in subclasses.
		 *
		 *
		 * The entity will be assigned the following data:
		 *
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
		 * Factory to create the entity
		 * @param classification
		 * The [MobCategory] of the entity
		 * @return A new [EntityBuilder] with reasonable default data generators.
		</P></T> */
		@JvmStatic
		fun <T : Entity, P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, factory: EntityFactory<T>, classification: MobCategory) =
			EntityBuilder(owner, parent, name, callback, factory, classification)
				.defaultLang()
	}
}
