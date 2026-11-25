package com.modularmc.registrate.providers.core

import net.minecraft.data.DataProvider
import net.minecraft.resources.ResourceLocation

/** 关联到单个注册条目的数据提供器委托。 */
interface RegistrateProviderDelegate<R : Any, T : R> : DataProvider {
	/** 返回由关联条目标识构成的任务名称。 */
	override fun getName(): String

	/** 返回当前生成任务对应的注册条目标识。 */
	fun getId(): ResourceLocation

	/** 返回当前生成任务关联的已绑定注册对象。 */
	fun getEntry(): T
}
