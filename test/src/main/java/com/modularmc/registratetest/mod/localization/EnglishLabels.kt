package com.modularmc.registratetest.mod.localization

import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.annotation.Trans
import com.modularmc.registrate.localization.annotation.TransVariants
import com.modularmc.registrate.localization.annotation.Translation
import com.modularmc.registrate.localization.annotation.Variant
import com.modularmc.registratetest.mod.TestMod

/** 用注解声明示例模组的电力状态变体和界面翻译键。 */
@TransVariants(
	modId = TestMod.MOD_ID,
	keyPrefix = "gui.testmod.power.",
	value = [
		Variant(id = "low", source = "Low power", translations = [Translation(MinecraftLocale.ZH_CN, "低功率")]),
		Variant(id = "high", source = "High power", translations = [Translation(MinecraftLocale.ZH_CN, "高功率")]),
	],
)
object EnglishLabels {
	@field:Trans(source = "Standby", translations = [Translation(MinecraftLocale.ZH_CN, "待机")])
	private const val standby = "gui.testmod.power.standby"

	@field:Trans(
		modId = TestMod.MOD_ID,
		key = "gui.testmod.kotlin_default_label",
		source = "Electric potential",
	)
	const val DEFAULT_LABEL = "gui.testmod.kotlin_default_label"

	@field:Trans(
		modId = TestMod.MOD_ID,
		key = "gui.testmod.container",
		source = "Test Container",
		translations = [Translation(MinecraftLocale.ZH_CN, "测试容器")],
	)
	const val MENU_TITLE = "gui.testmod.container"
}

private object StandaloneLabels {
	@field:Trans(modId = TestMod.MOD_ID, source = "Enable")
	private const val ENABLE = "gui.testmod.enable"

	@field:Trans(modId = TestMod.MOD_ID, valuePrefix = "gui.testmod.tier.", source = "Voltage tier: %s")
	private const val VOLTAGE_TIER = "voltage"

	@field:Trans(modId = TestMod.MOD_ID, namePrefix = "gui.testmod", source = "Advanced")
	private val advanced = false
}
