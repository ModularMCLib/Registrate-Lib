package com.modularmc.registrate.internal.util

import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.MarkerManager

/** 提供注册和数据生成日志的分类标记，便于在开发环境中按阶段筛选。 */
object DebugMarkers {
	private const val PREFIX = "REGISTRATE."

	@JvmField
	val REGISTER: Marker = marker("REGISTER")

	@JvmField
	val DATA: Marker = marker("DATA")

	private fun marker(name: String): Marker = MarkerManager.getMarker(PREFIX + name)
}
