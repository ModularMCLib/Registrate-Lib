package com.modularmc.registratetest.mod.localization

import com.modularmc.registrate.Registrate
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.annotation.Trans
import com.modularmc.registrate.localization.annotation.Translation
import com.modularmc.registratetest.mod.TestMod
import com.modularmc.registrate.util.nullness.NonNullBiConsumer

import net.minecraft.world.item.Item
import net.neoforged.fml.common.Mod

/** 使用简体中文作为源语言的第二个示例模组，用于覆盖跨命名空间的语言文件输出。 */
@Mod(ChineseLocaleTestMod.MOD_ID)
@Trans(
	modId = ChineseLocaleTestMod.MOD_ID,
	namespace = TestMod.MOD_ID,
	key = "gui.localetest.frequency",
	source = $$"频率：%1$s Hz",
	translations = [Translation(MinecraftLocale.EN_US, "Frequency: %s Hz")],
)
class ChineseLocaleTestMod {
	init {
		Registrate.create(MOD_ID).sourceLocale(MinecraftLocale.ZH_CN)
			.item("celestial_dust", ::Item)
			.localization { it.source("星尘").translation(MinecraftLocale.EN_US, "Celestial Dust") }
			.model {
				NonNullBiConsumer { context, generator ->
					generator.createWithExistingModel(context.entry, generator.mcLoc("item/glowstone_dust"))
				}
			}
			.register()
	}

	companion object {
		const val MOD_ID = "localetest"
	}
}
