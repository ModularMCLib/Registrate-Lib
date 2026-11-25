package com.modularmc.registrate.localization.text

import com.modularmc.mmclutils.utils.ColorUtils
import com.modularmc.mmclutils.utils.NumberUtils

import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.world.level.block.state.properties.Property

import it.unimi.dsi.fastutil.objects.ObjectArrayList

import java.util.function.UnaryOperator
import java.util.stream.Collector
import java.util.stream.Collectors

/** 构造和组合原版文本组件，保留子组件的翻译键和样式。 */
object ComponentUtils {
	/**
	 * 将方块状态属性名与序列化后的值组成一行。布尔值为真时用绿色，为假时用红色。
	 *
	 * @param property 要显示的方块状态属性
	 * @param value 该属性的值
	 * @return 包含属性名和属性值的组件
	 */
	@JvmStatic
	fun propertyText(property: Property<*>, value: Comparable<*>): MutableComponent {
		val name = Component.literal(Util.getPropertyName(property, value))
		if (value is Boolean) name.withStyle(if (value) ChatFormatting.GREEN else ChatFormatting.RED)
		return Component.literal(property.name).append(": ").append(name)
	}

	/** 将长整数按紧凑数值格式封装为字面组件。 */
	@JvmStatic
	fun numberText(value: Long): MutableComponent = Component.literal(NumberUtils.format(value))

	/** 将浮点数按紧凑数值格式封装为字面组件，保留非有限值的标准文本。 */
	@JvmStatic
	fun numberText(value: Double): MutableComponent = Component.literal(NumberUtils.format(value))

	/**
	 * 用翻译键显示百分数，数值最多保留两位小数，百分号由译文提供。
	 *
	 * @param translationKey 用于显示百分数的翻译键
	 * @param percent 已按百分数表示的数值
	 * @return 以格式化数值为参数的翻译组件
	 */
	@JvmStatic
	fun percentText(translationKey: String, percent: Double): MutableComponent = Component.translatable(translationKey, NumberUtils.formatTwoDecimals(percent))

	/** 创建一个可继续追加的列表，首行为给定的字面文本。 */
	@JvmStatic
	fun literalLine(text: String): MutableList<MutableComponent> = ObjectArrayList<MutableComponent>(1).apply { add(Component.literal(text)) }

	/** 将长整数的十进制文本放入单行可修改列表。 */
	@JvmStatic
	fun literalLine(value: Long): MutableList<MutableComponent> = literalLine(value.toString())

	/** 将浮点数的标准文本放入单行可修改列表。 */
	@JvmStatic
	fun literalLine(value: Double): MutableList<MutableComponent> = literalLine(value.toString())

	/** 按当前语言解析各行，用换行符拼接为纯文本。 */
	@JvmStatic
	fun plainText(lines: Iterable<Component>): String = lines.joinToString("\n") { it.string }

	/**
	 * 从单行文本读取整数。空列表视为零。
	 *
	 * @throws NumberFormatException 含多行、文本无法解析为整数或数值超出整数范围
	 */
	@JvmStatic
	fun parseInt(lines: List<Component>): Int = if (lines.isEmpty()) 0 else singleLineText(lines).toInt()

	/**
	 * 从单行文本读取浮点数。空列表视为零。
	 *
	 * @throws NumberFormatException 含多行或文本无法解析为浮点数
	 */
	@JvmStatic
	fun parseDouble(lines: List<Component>): Double = if (lines.isEmpty()) 0.0 else singleLineText(lines).toDouble()

	private fun singleLineText(lines: List<Component>): String {
		if (lines.size != 1) throw NumberFormatException("Expected a single numeric line")
		return lines.first().string
	}

	/**
	 * 将首行接到目标的末行，其余各行作为新行加入。目标为空时先创建首行。
	 *
	 * @param target 就地更新的目标列表
	 * @param lines 来自独立列表的待追加行
	 */
	@JvmStatic
	fun appendLines(target: MutableList<MutableComponent>, lines: List<Component>) {
		val iterator = lines.iterator()
		if (!iterator.hasNext()) return

		val lastLine = target.lastOrNull() ?: Component.empty().also { target.add(it) }
		lastLine.append(iterator.next())
		while (iterator.hasNext()) target.add(Component.empty().append(iterator.next()))
	}

	/**
	 * 将追加项接到目标组件上。两者都有自身内容或子组件时，在中间插入分隔符。
	 * 追加项为空时直接返回目标组件。
	 */
	@JvmStatic
	fun appendSeparated(target: MutableComponent, addition: Component, separator: Component): MutableComponent {
		if (!addition.hasContent()) return target
		if (target.hasContent()) target.append(separator)
		return target.append(addition)
	}

	private fun Component.hasContent(): Boolean = contents !== PlainTextContents.EMPTY || siblings.isNotEmpty()

	/** 给列表条目加引号后生成预览。超过五项时保留首尾各两项，中间显示省略号。 */
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

	/** 按遍历顺序拼接组件，在相邻项之间插入分隔符，并保留各子组件的翻译键和样式。 */
	@JvmStatic
	fun join(components: Iterable<Component>, separator: Component): MutableComponent {
		val result = Component.empty()
		val iterator = components.iterator()
		if (iterator.hasNext()) result.append(iterator.next())
		while (iterator.hasNext()) result.append(separator).append(iterator.next())
		return result
	}

	/** 创建与 [join] 一致的流收集器，按流的遍历顺序拼接组件。 */
	@JvmStatic
	fun joining(separator: Component): Collector<Component, *, MutableComponent> {
		val parts = Collectors.toCollection { ObjectArrayList<Component>() }
		return Collectors.collectingAndThen(parts) { join(it, separator) }
	}

	/**
	 * 按游戏刻从色板中循环选色，返回只修改文本颜色的样式变换。
	 *
	 * @param tick 调用方提供的当前游戏刻
	 * @param ticksPerColor 每种颜色持续的游戏刻数，至少为一
	 * @param colors 按使用顺序排列的色板，至少包含一种颜色
	 */
	@JvmStatic
	fun cyclingColor(tick: Long, ticksPerColor: Int, vararg colors: Int): UnaryOperator<Style> {
		val rgb = colors[cycleIndex(tick, ticksPerColor, colors.size)] and RGB_MASK
		return UnaryOperator { it.withColor(rgb) }
	}

	/**
	 * 按码点循环分配色板，色板起点随游戏刻推移，跨两个编码单元的字符作为整体着色。
	 * 每种颜色须持续至少一个游戏刻，色板至少包含一种颜色。
	 */
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

	/** 按圈数选取循环色相，返回只修改颜色的样式变换。 */
	@JvmStatic
	fun rainbow(turns: Double): UnaryOperator<Style> {
		val hue = (turns - Math.floor(turns)).toFloat()
		val rgb = ColorUtils.fromHsl(hue, 1f, 0.55f, 255) and RGB_MASK
		return UnaryOperator { it.withColor(rgb) }
	}

	/**
	 * 按文字码点在色板之间插值。首尾码点使用色板的两端颜色，单码点文本使用首色。
	 *
	 * @param text 待着色的文本
	 * @param colors 按渐变方向排列的色板，至少包含一种颜色
	 * @return 包含各个着色码点的文本组件
	 */
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
