package com.modularmc.registrate.providers.core

import net.minecraft.data.DataProvider
import net.neoforged.fml.LogicalSide

/** 声明数据提供器所属的逻辑侧，由 NeoForge 请求的数据生成侧决定是否启用。 */
interface RegistrateProvider : DataProvider {
	/** 返回当前提供器服务的逻辑侧，供数据生成流程选择任务。 */
	fun getSide(): LogicalSide
}
