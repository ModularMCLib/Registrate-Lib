package com.modularmc.registrate.builders

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.builders.base.AbstractBuilder
import com.modularmc.registrate.builders.base.BuilderCallback
import com.modularmc.registrate.internal.event.OneTimeEventReceiver.Companion.addModListener
import com.modularmc.registrate.internal.util.RegistrateDistExecutor.unsafeRunWhenOn
import com.modularmc.registrate.util.entry.BlockEntityEntry
import com.modularmc.registrate.util.entry.RegistryEntry
import com.modularmc.registrate.util.nullness.NonNullFunction
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.registries.DeferredHolder

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.util.function.Consumer
import java.util.function.Supplier

/**
 * A builder for block entities, allows for customization of the valid blocks.
 *
 * @param T
 * The type of block entity being built
 * @param P
 * Parent object type
 */
open class BlockEntityBuilder<T : BlockEntity, P : Any> protected constructor(
	owner: AbstractRegistrate<*>,
	parent: P,
	name: String,
	callback: BuilderCallback,
	private val factory: BlockEntityFactory<T>,
) : AbstractBuilder<BlockEntityType<*>, BlockEntityType<T>, P, BlockEntityBuilder<T, P>>(
	owner,
	parent,
	name,
	callback,
	Registries.BLOCK_ENTITY_TYPE,
) {
	fun interface BlockEntityFactory<T : BlockEntity> {
		fun create(type: BlockEntityType<T>, pos: BlockPos, state: BlockState): T
	}

	private val validBlocks = ObjectOpenHashSet<NonNullSupplier<out Block>>()
	private var renderer: NonNullSupplier<NonNullFunction<BlockEntityRendererProvider.Context, BlockEntityRenderer<T>>>? =
		null

	/**
	 * Add a valid block for this block entity.
	 *
	 * @param block
	 * A supplier for the block to add at registration time
	 * @return this [BlockEntityBuilder]
	 */
	open fun validBlock(block: NonNullSupplier<out Block>): BlockEntityBuilder<T, P> {
		validBlocks.add(block)
		return this
	}

	/**
	 * Add valid blocks for this block entity.
	 *
	 * @param blocks
	 * An array of suppliers for the block to add at registration time
	 * @return this [BlockEntityBuilder]
	 */
	@SafeVarargs
	fun validBlocks(vararg blocks: NonNullSupplier<out Block>): BlockEntityBuilder<T, P> {
		validBlocks.addAll(blocks.asList())
		return this
	}

	/**
	 * Register an [BlockEntityRenderer] for this block entity.
	 *
	 *
	 *
	 * @apiNote This requires the [Class] of the block entity object, which can only be gotten by inspecting an
	 * instance of it. Thus, the entity will be constructed to register the renderer.
	 *
	 * @param renderer
	 * A (server safe) supplier to an [Function] that will provide this block entity's renderer
	 * given the renderer dispatcher
	 * @return this [BlockEntityBuilder]
	 */
	open fun renderer(renderer: NonNullSupplier<NonNullFunction<BlockEntityRendererProvider.Context, BlockEntityRenderer<T>>>): BlockEntityBuilder<T, P> {
		if (this.renderer == null) { // First call only
			unsafeRunWhenOn(Dist.CLIENT, Supplier { Runnable { this.registerRenderer() } })
		}
		this.renderer = renderer
		return this
	}

	protected open fun registerRenderer() {
		addModListener<FMLClientSetupEvent>(
			owner,
			FMLClientSetupEvent::class.java,
			Consumer { `$`: FMLClientSetupEvent ->
				val renderer: NonNullSupplier<NonNullFunction<BlockEntityRendererProvider.Context, BlockEntityRenderer<T>>>? =
					this.renderer
				if (renderer != null) {
					BlockEntityRenderers.register<T>(
						entry,
						BlockEntityRendererProvider { t: BlockEntityRendererProvider.Context? ->
							renderer.get().apply(t!!)
						},
					)
				}
			},
		)
	}

	/**
	 * Register [Capabilities] for this block entity.
	 *
	 * @param registerCapabilitiesEvent A consumer for the register capabilities event
	 * @return this [BlockEntityBuilder]
	 */
	open fun registerCapability(registerCapabilitiesEvent: Consumer<RegisterCapabilitiesEvent>): BlockEntityBuilder<T, P> {
		addModListener<RegisterCapabilitiesEvent>(
			owner,
			RegisterCapabilitiesEvent::class.java,
			registerCapabilitiesEvent,
		)
		return this
	}

	override fun createEntry(): BlockEntityType<T> {
		val factory: BlockEntityFactory<T> = this.factory
		val supplier: NonNullSupplier<BlockEntityType<T>> = asSupplier()
		val blocks = validBlocks.iterator()
		val valid = Array(validBlocks.size) { blocks.next().get() }
		return BlockEntityType.Builder.of<T>({ pos, state -> factory.create(supplier.get(), pos, state) }, *valid).build(null)
	}

	override fun createEntryWrapper(delegate: DeferredHolder<BlockEntityType<*>, BlockEntityType<T>>): RegistryEntry<BlockEntityType<*>, BlockEntityType<T>> = BlockEntityEntry<T>(owner, delegate)

	public override fun register(): BlockEntityEntry<T> = super.register() as BlockEntityEntry<T>

	companion object {
		/**
		 * Create a new [BlockEntityBuilder] and configure data. Used in lieu of adding side-effects to constructor,
		 * so that alternate initialization strategies can be done in subclasses.
		 *
		 *
		 * The block entity will be assigned the following data:
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
		 * Factory to create the block entity
		 * @return A new [BlockEntityBuilder] with reasonable default data generators.
		</P></T> */
		@JvmStatic
		fun <T : BlockEntity, P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, factory: BlockEntityFactory<T>): BlockEntityBuilder<T, P> =
			BlockEntityBuilder<T, P>(owner, parent, name, callback, factory)
	}
}
