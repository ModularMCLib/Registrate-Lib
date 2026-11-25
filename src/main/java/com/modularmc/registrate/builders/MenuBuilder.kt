package com.modularmc.registrate.builders

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.builders.base.AbstractBuilder
import com.modularmc.registrate.builders.base.BuilderCallback
import com.modularmc.registrate.internal.event.OneTimeEventReceiver.Companion.addModListener
import com.modularmc.registrate.internal.util.RegistrateDistExecutor.unsafeRunWhenOn
import com.modularmc.registrate.util.entry.MenuEntry
import com.modularmc.registrate.util.entry.RegistryEntry
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.MenuAccess
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredHolder

/** 注册菜单类型，并在客户端为该类型绑定界面。菜单实例由打开菜单的游戏流程创建。 */
open class MenuBuilder<T : AbstractContainerMenu, S, P : Any>(
	owner: AbstractRegistrate<*>,
	parent: P,
	name: String,
	callback: BuilderCallback,
	private val factory: ForgeMenuFactory<T>,
	private val screenFactory: NonNullSupplier<ScreenFactory<T, S>>,
) : AbstractBuilder<MenuType<*>, MenuType<T>, P, MenuBuilder<T, S, P>>(
	owner,
	parent,
	name,
	callback,
	Registries.MENU,
) where S : Screen, S : MenuAccess<T> {
	/** 由模组提供，适用于只用菜单类型、窗口标识和物品栏即可创建的菜单。 */
	fun interface MenuFactory<T : AbstractContainerMenu> {
		/**
		 * 创建本次打开操作对应的菜单。
		 * @param type 已注册的菜单类型
		 * @param windowId 本次打开操作分配的窗口标识
		 * @param inv 打开菜单的玩家物品栏
		 * @return 新的菜单实例
		 */
		fun create(type: MenuType<T>, windowId: Int, inv: Inventory): T
	}

	/** 由模组提供，在普通菜单参数之外还可读取服务端发来的打开数据。 */
	fun interface ForgeMenuFactory<T : AbstractContainerMenu> {
		/**
		 * 使用当前窗口和玩家物品栏创建菜单。
		 * @param type 已注册的菜单类型
		 * @param windowId 本次打开操作分配的窗口标识
		 * @param inv 打开菜单的玩家物品栏
		 * @param buffer 可用时读取服务端附加数据，未附带数据时可为 `null`
		 * @return 新的菜单实例
		 */
		fun create(type: MenuType<T>, windowId: Int, inv: Inventory, buffer: RegistryFriendlyByteBuf?): T
	}

	/** 在客户端打开菜单时创建对应界面，界面可以通过 [MenuAccess] 访问该菜单。 */
	fun interface ScreenFactory<M : AbstractContainerMenu, T> where T : Screen, T : MenuAccess<M> {
		/**
		 * 为已经创建的菜单实例构造客户端界面。
		 * @param menu 当前打开的菜单
		 * @param inv 当前玩家的物品栏
		 * @param displayName 打开菜单时提供的标题组件
		 * @return 要显示的界面
		 */
		fun create(menu: M, inv: Inventory, displayName: Component): T
	}

	/** 使用只接收普通菜单参数的工厂构建菜单类型。界面工厂延迟到客户端注册时获取。 */
	constructor(
		owner: AbstractRegistrate<*>,
		parent: P,
		name: String,
		callback: BuilderCallback,
		factory: MenuFactory<T>,
		screenFactory: NonNullSupplier<ScreenFactory<T, S>>,
	) : this(
		owner,
		parent,
		name,
		callback,
		ForgeMenuFactory { type, windowId, inv, _ -> factory.create(type, windowId, inv) },
		screenFactory,
	)

	/** 创建菜单类型。客户端还会在界面注册事件中绑定对应的界面工厂。 */
	override fun createEntry(): MenuType<T> {
		val supplier = asSupplier()
		val menu = IMenuTypeExtension.create<T> { id, inventory, buffer ->
			factory.create(supplier.get(), id, inventory, buffer)
		}
		unsafeRunWhenOn(Dist.CLIENT) {
			Runnable {
				val screen = screenFactory.get()
				addModListener(owner, RegisterMenuScreensEvent::class.java) { event ->
					event.register<T, S>(menu) { container, inventory, title -> screen.create(container, inventory, title) }
				}
			}
		}
		return menu
	}

	/** 用菜单专用引用包装延迟绑定的条目。 */
	override fun createEntryWrapper(delegate: DeferredHolder<MenuType<*>, MenuType<T>>): RegistryEntry<MenuType<*>, MenuType<T>> = MenuEntry(owner, delegate)

	/** 提交菜单类型注册，返回供后续打开菜单使用的引用。 */
	override fun register(): MenuEntry<T> = super.register() as MenuEntry<T>
}
