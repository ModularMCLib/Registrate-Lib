package com.modularmc.registrate.localization.export

import com.modularmc.registrate.AbstractRegistrate

/** Requests a per-mod localization exchange document during DataGen. */
class LocalizationExportBuilder internal constructor(private val registrate: AbstractRegistrate<*>) {
	/** Registers localization export after the owning mod's language declarations have been collected. */
	fun build() {
		registrate.requestLocalizationExport()
	}
}
