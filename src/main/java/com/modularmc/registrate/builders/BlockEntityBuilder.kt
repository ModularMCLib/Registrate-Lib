package com.modularmc.registrate.builders

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.builders.base.AbstractBuilder
import com.modularmc.registrate.builders.base.BuilderCallback
import com.modularmc.registrate.internal.event.OneTimeEventReceiver.Companion.addModListener
import com.modularmc.registrate.internal.util.RegistrateDistExecutor.unsafeRunWhenOn
import com.modularmc.registrate.util.entry.BlockEntityEntry
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
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.registries.DeferredHolder

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.util.function.Consumer

/**
 * 构建和注册方块实体类型，登记可承载此类型的方块。
 *
 * @param T 方块实体类型
 * @param P 父级对象类型
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
	/** 由模组实现，用于游戏在指定位置创建方块实体实例。回调运行在发起创建的线程上。 */
	fun interface BlockEntityFactory<T : BlockEntity> {
		/**
		 * 创建与当前位置和方块状态对应的方块实体。
		 * @param type 已注册的方块实体类型
		 * @param pos 方块在世界中的位置
		 * @param state 创建时的方块状态
		 * @return 新的方块实体实例
		 */
		fun create(type: BlockEntityType<T>, pos: BlockPos, state: BlockState): T
	}

	private val validBlocks = ObjectOpenHashSet<NonNullSupplier<out Block>>()
	private var renderer: NonNullSupplier<NonNullFunction<BlockEntityRendererProvider.Context, BlockEntityRenderer<in T, *>>>? =
		null

	/**
	 * 添加一个可承载此方块实体的方块。
	 *
	 * @param block 注册时取得方块的供应器
	 * @return 当前构建器
	 */
	open fun validBlock(block: NonNullSupplier<out Block>): BlockEntityBuilder<T, P> {
		validBlocks.add(block)
		return this
	}

	/**
	 * 添加多个可承载此方块实体的方块。
	 *
	 * @param blocks 注册时取得各个方块的供应器
	 * @return 当前构建器
	 */
	@SafeVarargs
	fun validBlocks(vararg blocks: NonNullSupplier<out Block>): BlockEntityBuilder<T, P> {
		validBlocks.addAll(blocks.asList())
		return this
	}

	/**
	 * 为此方块实体类型登记客户端渲染器。
	 *
	 * @param renderer 外层供应器须可在服务端安全创建，内层工厂在客户端接收渲染上下文并创建渲染器
	 * @return 当前构建器
	 */
	open fun renderer(renderer: NonNullSupplier<NonNullFunction<BlockEntityRendererProvider.Context, BlockEntityRenderer<in T, *>>>): BlockEntityBuilder<T, P> {
		if (this.renderer == null) { // 首次设置时绑定渲染事件，后续设置由该监听器读取。
			unsafeRunWhenOn(Dist.CLIENT) { Runnable { this.registerRenderer() } }
		}
		this.renderer = renderer
		return this
	}

	protected open fun registerRenderer() {
		addModListener(
			owner,
			FMLClientSetupEvent::class.java,
			Consumer {
				val provider = renderer!!.get()
				BlockEntityRenderers.register(entry) { provider.apply(it) }
			},
		)
	}

	/**
	 * 在能力注册事件中为此方块实体登记能力。
	 *
	 * @param registerCapabilitiesEvent 接收能力注册事件的回调
	 * @return 当前构建器
	 */
	open fun registerCapability(registerCapabilitiesEvent: Consumer<RegisterCapabilitiesEvent>): BlockEntityBuilder<T, P> {
		addModListener(
			owner,
			RegisterCapabilitiesEvent::class.java,
			registerCapabilitiesEvent,
		)
		return this
	}

	/** 在注册时收集有效方块，并将实例工厂与新类型绑定。 */
	override fun createEntry(): BlockEntityType<T> {
		val factory: BlockEntityFactory<T> = this.factory
		val supplier: NonNullSupplier<BlockEntityType<T>> = asSupplier()
		val blocks = validBlocks.iterator()
		val valid = Array(validBlocks.size) { blocks.next().get() }
		return BlockEntityType({ pos, state -> factory.create(supplier.get(), pos, state) }, *valid)
	}

	/** 用方块实体类型的专用引用包装延迟绑定的条目。 */
	override fun createEntryWrapper(delegate: DeferredHolder<BlockEntityType<*>, BlockEntityType<T>>) = BlockEntityEntry(owner, delegate)

	/** 提交方块实体类型注册，返回用于创建或查询实例的类型引用。 */
	override fun register(): BlockEntityEntry<T> = super.register() as BlockEntityEntry<T>

	companion object {
		/**
		 * 创建 [BlockEntityBuilder]，供子类在此入口附加初始化配置。
		 *
		 * @param T 方块实体类型
		 * @param P 父级对象类型
		 * @param owner 所属的 [AbstractRegistrate] 实例
		 * @param parent 构建完成后返回的父级对象
		 * @param name 条目名称
		 * @param callback 将条目提交给所属实例的回调
		 * @param factory 创建方块实体的工厂
		 * @return 新的方块实体构建器
		 */
		@JvmStatic
		fun <T : BlockEntity, P : Any> create(owner: AbstractRegistrate<*>, parent: P, name: String, callback: BuilderCallback, factory: BlockEntityFactory<T>) =
			BlockEntityBuilder(owner, parent, name, callback, factory)
	}
}
