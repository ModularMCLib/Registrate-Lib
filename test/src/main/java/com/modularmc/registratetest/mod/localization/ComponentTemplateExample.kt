package com.modularmc.registratetest.mod.localization

import com.modularmc.registrate.Registrate
import com.modularmc.registrate.localization.ComponentTemplate
import com.modularmc.registrate.localization.MinecraftLocale.ZH_CN
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registratetest.mod.TestMod

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.TranslatableContents
import net.neoforged.testframework.DynamicTest
import net.neoforged.testframework.annotation.ForEachTest
import net.neoforged.testframework.annotation.TestHolder
import net.neoforged.testframework.gametest.EmptyTemplate
import net.minecraft.gametest.framework.GameTest

/** 演示多个本地化提示行共用一个模板和参数列表。 */
@ForEachTest(groups = ["registrate-runtime"])
object ComponentTemplateExample {
	private val amount = TranslationKey(TestMod.MOD_ID, "tooltip.testmod.template.amount")
	private val unit = TranslationKey(TestMod.MOD_ID, "tooltip.testmod.template.unit")
	private val summary = TranslationKey(TestMod.MOD_ID, "tooltip.testmod.template.summary")
	private val headingStyle = Style.EMPTY.withColor(ChatFormatting.GOLD)
	private val detailStyle = Style.EMPTY.withItalic(true)
	private val template = ComponentTemplate(
		ComponentTemplate.Line(amount, headingStyle),
		ComponentTemplate.Line(unit, detailStyle),
		ComponentTemplate.Line(summary, Style.EMPTY),
	)

	/** 为模板使用的键登记英文源文和简体中文译文。 */
	@JvmStatic
	fun declare(registrate: Registrate) {
		registrate.addLocalization(amount) { it.source("Required %1\$s").translation(ZH_CN, "需要 %1\$s") }
		registrate.addLocalization(unit) { it.source("Unit %2\$s").translation(ZH_CN, "单位 %2\$s") }
		registrate.addLocalization(summary) { it.source("%2\$s x %1\$s").translation(ZH_CN, "%1\$s 个 %2\$s") }
	}

	/** 检查多行模板的参数和样式，并验证多次构建产生独立组件。 */
	@JvmStatic
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(description = ["Build localized template lines with shared arguments and independent components"])
	fun buildsIndependentLocalizedLines(test: DynamicTest) {
		test.onGameTest { helper ->
			val first = template.build(64, "coils")
			val second = template.build(2, "plates")
			val firstContents = first.map { it.contents as TranslatableContents }
			val secondContents = second.map { it.contents as TranslatableContents }
			helper.assertValueEqual(listOf(amount.value, unit.value, summary.value), firstContents.map { it.key }, "Line order")
			helper.assertValueEqual(listOf(headingStyle, detailStyle, Style.EMPTY), first.map { it.style }, "Line styles")
			helper.assertTrue(firstContents.all { it.args.contentEquals(arrayOf<Any>(64, "coils")) }, "Arguments are shared by index")
			helper.assertTrue(secondContents.all { it.args.contentEquals(arrayOf<Any>(2, "plates")) }, "New builds use their own arguments")
			(first.first() as MutableComponent).append(" changed")
			firstContents.first().args[0] = 0
			helper.assertTrue(second.first().siblings.isEmpty(), "Components are independently mutable")
			helper.assertValueEqual(64, firstContents[1].args[0], "Lines have independent argument arrays")
			helper.assertValueEqual(2, secondContents.first().args[0], "Builds have independent argument arrays")
			helper.succeed()
		}
	}
}
