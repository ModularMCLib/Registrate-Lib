package com.modularmc.registrate.export

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.localization.export.LocalizationExportBuilder

/** Starts export builders for the data produced by one Registrate instance. */
class RegistrateExportBuilder internal constructor(private val registrate: AbstractRegistrate<*>) {
	/** Configures localization exchange output for the owning mod. */
	fun localization(): LocalizationExportBuilder = LocalizationExportBuilder(registrate)
}
