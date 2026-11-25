package com.modularmc.registrate.localization.export

import com.modularmc.registrate.AbstractRegistrate

import java.nio.file.Path

/** 为所属模组配置数据生成时导出的本地化交换文件。 */
class LocalizationExportBuilder internal constructor(private val registrate: AbstractRegistrate<*>) {
	private var outputPath: Path? = null

	/** 设置导出 JSON 的完整文件路径。相对路径以当前进程的工作目录为基准。 */
	fun path(path: Path): LocalizationExportBuilder {
		outputPath = path.toAbsolutePath().normalize()
		return this
	}

	/**
	 * 登记本地化导出任务，由数据生成流程在收集所属模组的语言声明后写出文件。
	 *
	 * @throws IllegalArgumentException 尚未设置输出路径
	 */
	fun build() {
		registrate.requestLocalizationExport(requireNotNull(outputPath) { "Set the localization export path before build" })
	}
}
