package com.modularmc.registrate.localization

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/**
 * Builds reusable localized lines for tooltips and other multi-line text.
 * Declare the text for each [Line.key] through the owning Registrate instance.
 */
class ComponentTemplate(vararg lines: Line) {
	/** A translation key and the style applied to its line. */
	@JvmRecord
	data class Line(val key: TranslationKey, val style: Style)

	private val lines = lines.toList()

	/** Builds fresh components in declaration order. Every line can reference the same arguments by index. */
	fun build(vararg args: Any): List<Component> = lines.map { line ->
		Component.translatable(line.key.value, *args).setStyle(line.style)
	}
}
