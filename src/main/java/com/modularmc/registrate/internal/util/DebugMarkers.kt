package com.modularmc.registrate.internal.util

import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.MarkerManager

object DebugMarkers {
	private const val PREFIX = "REGISTRATE."

	@JvmField
	val REGISTER: Marker = marker("REGISTER")

	@JvmField
	val DATA: Marker = marker("DATA")

	private fun marker(name: String): Marker = MarkerManager.getMarker(PREFIX + name)
}
