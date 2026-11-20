package com.modularmc.registrate.test.mod.localization;

import com.modularmc.registrate.Registrate;
import com.modularmc.registrate.localization.MinecraftLocale;
import com.modularmc.registrate.localization.annotation.Trans;
import com.modularmc.registrate.localization.annotation.Translation;
import com.modularmc.registrate.test.mod.TestMod;

import net.minecraft.world.item.Item;
import net.neoforged.fml.common.Mod;

@Mod(ChineseLocaleTestMod.MOD_ID)
@Trans(modId = ChineseLocaleTestMod.MOD_ID,
       namespace = TestMod.MOD_ID,
       key = "gui.localetest.frequency",
       source = "频率：%1$s Hz",
       translations = @Translation(locale = MinecraftLocale.EN_US, text = "Frequency: %s Hz"))
public final class ChineseLocaleTestMod {

    public static final String MOD_ID = "localetest";

    public ChineseLocaleTestMod() {
        Registrate.create(MOD_ID).sourceLocale(MinecraftLocale.ZH_CN)
                .item("celestial_dust", Item::new)
                .localization(text -> text.source("星尘").translation(MinecraftLocale.EN_US, "Celestial Dust"))
                .model(() -> (context, generator) -> generator.createWithExistingModel(context.getEntry(), generator.mcLoc("item/glowstone_dust")))
                .register();
    }
}
