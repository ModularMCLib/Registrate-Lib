package com.modularmc.registrate.localization.terminology

/** A stable, extensible field used to associate terms and localization declarations. */
@JvmRecord
data class Domain(val id: String) {
	companion object {
		@JvmField val CHEMISTRY = Domain("chemistry")

		@JvmField val PHYSICS = Domain("physics")

		@JvmField val ENERGY = Domain("energy")

		@JvmField val ASTRONOMY = Domain("astronomy")

		@JvmField val GEOGRAPHY = Domain("geography")

		@JvmField val GEOLOGY = Domain("geology")

		@JvmField val ENGINEERING = Domain("engineering")

		@JvmField val MATERIALS = Domain("materials")

		@JvmField val COMPUTING = Domain("computing")

		@JvmField val MAGIC = Domain("magic")

		@JvmField val ADVENTURE = Domain("adventure")
	}
}
