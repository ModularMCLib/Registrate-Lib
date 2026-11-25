package com.modularmc.registrate

import com.modularmc.registrate.internal.util.RegistrateLogger

import net.neoforged.fml.ModList

/** 一个模组的默认注册入口。在初始化阶段通过 [create] 创建并绑定到模组事件总线。 */
open class Registrate protected constructor(modid: String) : AbstractRegistrate<Registrate>(modid) {
	companion object {
		private val LOGGER = RegistrateLogger.core()

		/** 创建一个 Registrate 实例，并将监听器绑定到所属模组的事件总线。 */
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
