package com.modularmc.registrate.localization

/** 标识文本在模组中的用途，模组可以定义自己的类型。 */
@JvmRecord
data class TextType(val id: String) {
	companion object {
		@JvmField val ITEM = TextType("item")

		@JvmField val BLOCK = TextType("block")

		@JvmField val MACHINE = TextType("machine")

		@JvmField val MATERIAL = TextType("material")

		@JvmField val PROCESS = TextType("process")

		@JvmField val REACTION = TextType("reaction")

		@JvmField val PHENOMENON = TextType("phenomenon")

		@JvmField val ASTRONOMICAL_OBJECT = TextType("astronomical_object")

		@JvmField val BIOME = TextType("biome")

		@JvmField val RESEARCH = TextType("research")

		@JvmField val STATISTIC = TextType("statistic")

		@JvmField val DESCRIPTION = TextType("description")

		@JvmField val TOOLTIP = TextType("tooltip")

		@JvmField val GUI_LABEL = TextType("gui_label")
	}
}
