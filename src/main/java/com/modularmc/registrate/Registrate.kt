package com.modularmc.registrate

import com.modularmc.registrate.internal.util.RegistrateLogger

import net.neoforged.fml.ModList

open class Registrate protected constructor(modid: String) : AbstractRegistrate<Registrate>(modid) {
	companion object {
		private val LOGGER = RegistrateLogger.core()

		/** Creates a Registrate and attaches its listeners to the owning mod's event bus. */
		@JvmStatic
		fun create(modid: String): Registrate {
			val registrate = Registrate(modid)
			val bus = ModList.get().getModContainerById(modid).orElse(null)?.eventBus
			if (bus != null) {
				registrate.registerEventListeners(bus)
			} else {
				LOGGER.fatal(RegistrateLogger.modMessage("Failed to locate the mod event bus during Registrate.create; registration listeners were not attached"), modid)
			}
			return registrate
		}
	}
}
