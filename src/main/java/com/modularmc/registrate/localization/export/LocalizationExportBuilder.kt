package com.modularmc.registrate.localization.export

import com.modularmc.registrate.AbstractRegistrate

import java.nio.file.Path

/** Requests a per-mod localization exchange document during DataGen. */
class LocalizationExportBuilder internal constructor(private val registrate: AbstractRegistrate<*>) {
	private var outputPath: Path? = null

	/** Sets the exact JSON file path used for this mod's export. */
	fun path(path: Path): LocalizationExportBuilder {
		outputPath = path.toAbsolutePath().normalize()
		return this
	}

	/** Registers localization export after the owning mod's language declarations have been collected. */
	fun build() {
		registrate.requestLocalizationExport(requireNotNull(outputPath) { "Set the localization export path before build" })
	}
}
