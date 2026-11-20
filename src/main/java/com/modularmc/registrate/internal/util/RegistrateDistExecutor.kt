package com.modularmc.registrate.internal.util

import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.loading.FMLEnvironment

import java.util.function.Supplier

object RegistrateDistExecutor {
	@JvmStatic
	fun unsafeRunWhenOn(dist: Dist, toRun: Supplier<Runnable>) {
		if (dist == FMLEnvironment.dist) {
			toRun.get().run()
		}
	}
}
