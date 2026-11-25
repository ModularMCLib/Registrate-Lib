package com.modularmc.registrate.localization.text

import com.modularmc.mmclutils.utils.ColorUtils
import com.modularmc.mmclutils.utils.NumberUtils

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.util.Util
import net.minecraft.world.level.block.state.properties.Property

import it.unimi.dsi.fastutil.objects.ObjectArrayList

import java.util.function.UnaryOperator
import java.util.stream.Collector
import java.util.stream.Collectors

/** Builds and combines Minecraft text components while preserving their translation keys and styles. */
object ComponentUtils {
	/** Displays a block-state property. Boolean values are colored green or red. */
	@JvmStatic
	fun propertyText(property: Property<*>, value: Comparable<*>): MutableComponent {
		val name = Component.literal(Util.getPropertyName(property, value))
		if (value is Boolean) name.withStyle(if (value) ChatFormatting.GREEN else ChatFormatting.RED)
		return Component.literal(property.name).append(": ").append(name)
	}

	@JvmStatic
	fun numberText(value: Long): MutableComponent = Component.literal(NumberUtils.format(value))

	@JvmStatic
	fun numberText(value: Double): MutableComponent = Component.literal(NumberUtils.format(value))

	/** Supplies a number with up to two decimal places to the given translation key. */
	@JvmStatic
	fun percentText(translationKey: String, percent: Double): MutableComponent = Component.translatable(translationKey, NumberUtils.formatTwoDecimals(percent))

	/** Creates an editable list containing one literal line. */
	@JvmStatic
	fun literalLine(text: String): MutableList<MutableComponent> = ObjectArrayList<MutableComponent>(1).apply { add(Component.literal(text)) }

	@JvmStatic
	fun literalLine(value: Long): MutableList<MutableComponent> = literalLine(value.toString())

	@JvmStatic
	fun literalLine(value: Double): MutableList<MutableComponent> = literalLine(value.toString())

	/** Renders the components as plain text using a newline between lines. */
	@JvmStatic
	fun plainText(lines: Iterable<Component>): String = lines.joinToString("\n") { it.string }

	/** Reads a single integer line. An empty list represents zero. */
	@JvmStatic
	fun parseInt(lines: List<Component>): Int = if (lines.isEmpty()) 0 else singleLineText(lines).toInt()

	/** Reads a single decimal line. An empty list represents zero. */
	@JvmStatic
	fun parseDouble(lines: List<Component>): Double = if (lines.isEmpty()) 0.0 else singleLineText(lines).toDouble()

	private fun singleLineText(lines: List<Component>): String {
		if (lines.size != 1) throw NumberFormatException("Expected a single numeric line")
		return lines.first().string
	}

	/** Appends the first supplied line to the last target line and adds the rest as new lines. */
	@JvmStatic
	fun appendLines(target: MutableList<MutableComponent>, lines: List<Component>) {
		val iterator = lines.iterator()
		if (!iterator.hasNext()) return

		val lastLine = target.lastOrNull() ?: Component.empty().also { target.add(it) }
		lastLine.append(iterator.next())
		while (iterator.hasNext()) target.add(Component.empty().append(iterator.next()))
	}

	/** Appends a separator when both components contain text or children. */
	@JvmStatic
	fun appendSeparated(target: MutableComponent, addition: Component, separator: Component): MutableComponent {
		if (!addition.hasContent()) return target
		if (target.hasContent()) target.append(separator)
		return target.append(addition)
	}

	private fun Component.hasContent(): Boolean = contents !== PlainTextContents.EMPTY || siblings.isNotEmpty()

	/** Quotes each entry. Previews over five entries keep the first and last two. */
	@JvmStatic
	fun previewList(entries: List<String>, entryStyle: Style): MutableComponent {
		val preview = ObjectArrayList<Component>(minOf(entries.size, 5))
		if (entries.size <= 5) {
			entries.mapTo(preview) { quoted(it, entryStyle) }
		} else {
			preview.add(quoted(entries.first(), entryStyle))
			preview.add(quoted(entries[1], entryStyle))
			preview.add(Component.literal("…"))
			preview.add(quoted(entries[entries.lastIndex - 1], entryStyle))
			preview.add(quoted(entries.last(), entryStyle))
		}
		return Component.literal("[").append(join(preview, Component.literal(", "))).append("]")
	}

	private fun quoted(text: String, style: Style): MutableComponent = Component.literal("\"$text\"").setStyle(style)

	/** Joins components in iteration order, preserving their styles and translations. */
	@JvmStatic
	fun join(components: Iterable<Component>, separator: Component): MutableComponent {
		val result = Component.empty()
		val iterator = components.iterator()
		if (iterator.hasNext()) result.append(iterator.next())
		while (iterator.hasNext()) result.append(separator).append(iterator.next())
		return result
	}

	/** Collects stream elements in encounter order before joining them. */
	@JvmStatic
	fun joining(separator: Component): Collector<Component, *, MutableComponent> = Collectors.collectingAndThen(Collectors.toList<Component>()) { join(it, separator) }

	/** Selects a palette color at the given tick. */
	@JvmStatic
	fun cyclingColor(tick: Long, ticksPerColor: Int, vararg colors: Int): UnaryOperator<Style> {
		val rgb = colors[cycleIndex(tick, ticksPerColor, colors.size)] and RGB_MASK
		return UnaryOperator { it.withColor(rgb) }
	}

	/** Colors successive Unicode code points with a palette that shifts as ticks advance. */
	@JvmStatic
	fun scrollingColor(text: String, tick: Long, ticksPerColor: Int, vararg colors: Int): MutableComponent {
		var paletteIndex = Math.floorMod(-cycleIndex(tick, ticksPerColor, colors.size), colors.size)
		return colorText(text) {
			val rgb = colors[paletteIndex] and RGB_MASK
			paletteIndex++
			if (paletteIndex == colors.size) paletteIndex = 0
			rgb
		}
	}

	private fun cycleIndex(tick: Long, ticksPerColor: Int, colorCount: Int): Int {
		if (ticksPerColor < 1 || colorCount == 0) throw IllegalArgumentException("A color sequence needs a palette and a positive tick duration")
		return Math.floorMod(Math.floorDiv(tick, ticksPerColor), colorCount)
	}

	/** Creates a color transformation from a hue measured in turns. */
	@JvmStatic
	fun rainbow(turns: Double): UnaryOperator<Style> {
		val hue = (turns - Math.floor(turns)).toFloat()
		val rgb = ColorUtils.fromHsl(hue, 1f, 0.55f, 255) and RGB_MASK
		return UnaryOperator { it.withColor(rgb) }
	}

	/** Applies a palette gradient across the Unicode code points of the text. */
	@JvmStatic
	fun gradient(text: String, vararg colors: Int): MutableComponent {
		if (colors.isEmpty()) throw IllegalArgumentException("A text gradient needs at least one color")
		val last = (Character.codePointCount(text, 0, text.length) - 1).coerceAtLeast(1)
		return colorText(text) { index -> ColorUtils.gradient(colors, index.toFloat() / last) and RGB_MASK }
	}

	private inline fun colorText(text: String, colorAt: (Int) -> Int): MutableComponent {
		val result = Component.empty()
		var offset = 0
		var index = 0
		while (offset < text.length) {
			val end = offset + Character.charCount(Character.codePointAt(text, offset))
			result.append(Component.literal(text.substring(offset, end)).withColor(colorAt(index)))
			offset = end
			index++
		}
		return result
	}

	private const val RGB_MASK = 0xffffff
}
