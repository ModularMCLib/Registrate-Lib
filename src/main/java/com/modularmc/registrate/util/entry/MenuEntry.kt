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

open class MenuEntry<T : AbstractContainerMenu>(owner: AbstractRegistrate<*>, delegate: DeferredHolder<MenuType<*>, MenuType<T>>) : RegistryEntry<MenuType<*>, MenuType<T>>(owner, delegate) {
	open fun create(windowId: Int, inventory: Inventory): T = get().create(windowId, inventory)

	open fun asProvider(): MenuConstructor = MenuConstructor { windowId, inventory, _ -> create(windowId, inventory) }

	open fun open(player: ServerPlayer, displayName: Component) {
		open(player, displayName, asProvider())
	}

	open fun open(player: ServerPlayer, displayName: Component, extraData: Consumer<RegistryFriendlyByteBuf>) {
		open(player, displayName, asProvider(), extraData)
	}

	open fun open(player: ServerPlayer, displayName: Component, provider: MenuConstructor) {
		player.openMenu(SimpleMenuProvider(provider, displayName))
	}

	open fun open(player: ServerPlayer, displayName: Component, provider: MenuConstructor, extraData: Consumer<RegistryFriendlyByteBuf>) {
		player.openMenu(SimpleMenuProvider(provider, displayName), extraData)
	}
}
