package com.modularmc.registrate.localization.terminology

/** Built-in fields used to associate terms and localization declarations. */
enum class Domain(private val id: String) {
	CHEMISTRY("chemistry"),
	PHYSICS("physics"),
	ENERGY("energy"),
	ASTRONOMY("astronomy"),
	GEOGRAPHY("geography"),
	GEOLOGY("geology"),
	ENGINEERING("engineering"),
	MATERIALS("materials"),
	COMPUTING("computing"),
	MAGIC("magic"),
	ADVENTURE("adventure"),
	;

	fun id(): String = id
}
