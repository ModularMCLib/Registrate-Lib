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
import net.neoforged.neoforge.network.IContainerFactory
import net.neoforged.neoforge.registries.DeferredHolder

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
	fun interface MenuFactory<T : AbstractContainerMenu> {
		fun create(type: MenuType<T>, windowId: Int, inv: Inventory): T
	}

	fun interface ForgeMenuFactory<T : AbstractContainerMenu> {
		fun create(type: MenuType<T>, windowId: Int, inv: Inventory, buffer: RegistryFriendlyByteBuf?): T
	}

	fun interface ScreenFactory<M : AbstractContainerMenu, T> where T : Screen, T : MenuAccess<M> {
		fun create(menu: M, inv: Inventory, displayName: Component): T
	}

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

	override fun createEntry(): MenuType<T> {
		val supplier = asSupplier()
		val menu = IMenuTypeExtension.create<T>(
			IContainerFactory { id, inventory, buffer ->
				factory.create(supplier.get(), id, inventory!!, buffer)
			},
		)
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

	override fun createEntryWrapper(delegate: DeferredHolder<MenuType<*>, MenuType<T>>): RegistryEntry<MenuType<*>, MenuType<T>> = MenuEntry<T>(owner, delegate)

	public override fun register(): MenuEntry<T> = super.register() as MenuEntry<T>
}
