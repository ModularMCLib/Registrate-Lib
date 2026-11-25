package com.modularmc.registrate.internal.util

import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.loading.FMLEnvironment

import java.util.function.Supplier

/** 根据 NeoForge 的物理侧执行延迟任务，供客户端类的初始化入口使用。 */
object RegistrateDistExecutor {
	/** 当前物理侧与 [dist] 匹配时，才获取 [toRun] 并在调用线程执行。客户端任务的类引用应放在取值器内部。 */
	@JvmStatic
	fun unsafeRunWhenOn(dist: Dist, toRun: Supplier<Runnable>) {
		if (dist == FMLEnvironment.getDist()) {
			toRun.get().run()
		}
	}
}
