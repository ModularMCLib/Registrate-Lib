package com.modularmc.registrate.internal.lifecycle

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.internal.event.OneTimeEventReceiver

import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.registries.RegisterEvent

import java.util.function.Consumer

/** Attaches the registration and DataGen listeners for one Registrate. */
class RegistrateLifecycle(private val owner: AbstractRegistrate<*>, private val hooks: Hooks) {
	/** Callbacks bound to their owning Registrate before NeoForge posts lifecycle events. */
	interface Hooks {
		fun modEventBus(): IEventBus?

		fun setModEventBus(bus: IEventBus)

		fun doDatagen(): Boolean

		fun onRegister(event: RegisterEvent)

		fun onRegisterLate(event: RegisterEvent)

		fun onBuildCreativeModeTabContents(event: BuildCreativeModeTabContentsEvent)

		fun onData(event: GatherDataEvent)
	}

	fun register(bus: IEventBus) {
		if (hooks.modEventBus() == null) {
			hooks.setModEventBus(bus)
		}

		val onRegister = Consumer(hooks::onRegister)
		val onRegisterLate = Consumer(hooks::onRegisterLate)
		bus.addListener(EventPriority.NORMAL, RegisterEvent::class.java, onRegister)
		bus.addListener(EventPriority.LOWEST, RegisterEvent::class.java, onRegisterLate)
		bus.addListener(BuildCreativeModeTabContentsEvent::class.java, hooks::onBuildCreativeModeTabContents)

		OneTimeEventReceiver.addModListener(owner, FMLCommonSetupEvent::class.java) {
			OneTimeEventReceiver.unregister(owner, onRegister, RegisterEvent::class.java)
			OneTimeEventReceiver.unregister(owner, onRegisterLate, RegisterEvent::class.java)
		}

		if (hooks.doDatagen()) {
			OneTimeEventReceiver.addModListener(owner, GatherDataEvent::class.java, hooks::onData)
		}
	}
}
