package com.modularmc.registrate.export

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.localization.export.LocalizationExportBuilder

/** 提供当前 Registrate 实例的数据导出入口。 */
class RegistrateExportBuilder internal constructor(private val registrate: AbstractRegistrate<*>) {
	/** 配置所属模组的本地化交换文件。 */
	fun localization(): LocalizationExportBuilder = LocalizationExportBuilder(registrate)
}
