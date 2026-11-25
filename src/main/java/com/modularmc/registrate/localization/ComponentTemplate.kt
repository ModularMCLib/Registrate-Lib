package com.modularmc.registrate.localization

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/**
 * 把翻译键和样式组成可复用的文本模板，供提示框等多行文本使用。
 * 模组初始化时通过 `addLocalization` 为每行的键声明译文，显示时通过 [build] 传入实际参数。
 *
 * @param lines 按显示顺序排列的文本行
 */
class ComponentTemplate(vararg lines: Line) {
	/**
	 * 模板中的一行，正文和说明都可以使用。
	 *
	 * @param key 该行使用的翻译键
	 * @param style 应用在该行组件上的样式
	 */
	@JvmRecord
	data class Line(val key: TranslationKey, val style: Style)

	private val lines = lines.toList()

	/**
	 * 为一次显示创建新的组件列表。每行可以用 `%1$s`、`%2$s` 等占位符引用同一组参数。
	 *
	 * @param args 传给各行翻译组件的参数
	 * @return 按声明顺序排列的组件列表
	 */
	fun build(vararg args: Any): List<Component> = lines.map { line ->
		Component.translatable(line.key.value, *args).setStyle(line.style)
	}
}
