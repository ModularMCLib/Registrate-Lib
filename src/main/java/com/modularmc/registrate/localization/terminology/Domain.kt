package com.modularmc.registrate.localization.terminology

/** 通过稳定标识关联术语和本地化声明。模组可以定义自己的领域。 */
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
