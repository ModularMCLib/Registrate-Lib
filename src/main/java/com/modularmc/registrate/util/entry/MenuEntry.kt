package com.modularmc.registrate.util.entry

import com.modularmc.registrate.AbstractRegistrate

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuConstructor
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.registries.DeferredHolder

import java.util.function.Consumer

/** 菜单类型的延迟引用。类型注册后，可用它创建菜单实例或在服务端为玩家打开菜单。 */
open class MenuEntry<T : AbstractContainerMenu>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<MenuType<*>, MenuType<T>>) : RegistryEntry<MenuType<*>, MenuType<T>>(owner, delegate) {
	/** 按窗口标识和物品栏创建菜单实例，菜单类型须已经注册。 */
	open fun create(windowId: Int, inventory: Inventory): T = get().create(windowId, inventory)

	/** 返回可传给原版菜单打开流程的构造器。创建菜单时使用此引用绑定的类型。 */
	open fun asProvider(): MenuConstructor = MenuConstructor { windowId, inventory, _ -> create(windowId, inventory) }

	/** 在服务端用此引用的菜单构造器为玩家打开菜单，并显示指定标题。 */
	open fun open(player: ServerPlayer, displayName: Component) {
		open(player, displayName, asProvider())
	}

	/** 使用此引用的菜单构造器打开菜单，并通过 [extraData] 向客户端发送附加数据。 */
	open fun open(player: ServerPlayer, displayName: Component, extraData: Consumer<RegistryFriendlyByteBuf>) {
		open(player, displayName, asProvider(), extraData)
	}

	/** 使用 [provider] 创建本次打开的菜单，可由它提供当前交互所需的容器或其他状态。 */
	open fun open(player: ServerPlayer, displayName: Component, provider: MenuConstructor) {
		player.openMenu(SimpleMenuProvider(provider, displayName))
	}

	/**
	 * 在服务端打开自定义构造的菜单，并向客户端发送创建菜单所需的附加数据。
	 *
	 * @param player 打开菜单的服务端玩家
	 * @param displayName 客户端显示的菜单标题
	 * @param provider 在服务端使用的菜单构造器
	 * @param extraData 按客户端工厂约定的顺序写入附加数据
	 */
	open fun open(player: ServerPlayer, displayName: Component, provider: MenuConstructor, extraData: Consumer<RegistryFriendlyByteBuf>) {
		player.openMenu(SimpleMenuProvider(provider, displayName), extraData)
	}
}
