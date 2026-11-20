package com.modularmc.registrate.test.mod.localization;

import com.modularmc.registrate.localization.MinecraftLocale;
import com.modularmc.registrate.localization.annotation.Trans;
import com.modularmc.registrate.localization.annotation.TransVariants;
import com.modularmc.registrate.localization.annotation.Translation;
import com.modularmc.registrate.localization.annotation.Variant;
import com.modularmc.registrate.test.mod.TestMod;

@TransVariants(modId = TestMod.MOD_ID,
               keyPrefix = "gui.testmod.power",
               value = {
                       @Variant(id = "low", source = "Low power", translations = @Translation(locale = MinecraftLocale.ZH_CN, text = "低功率")),
                       @Variant(id = "high", source = "High power", translations = @Translation(locale = MinecraftLocale.ZH_CN, text = "高功率"))
               })
public final class EnglishLabels {

    private EnglishLabels() {}

    @Trans(modId = TestMod.MOD_ID,
           key = "gui.testmod.container",
           source = "Test Container",
           translations = @Translation(locale = MinecraftLocale.ZH_CN, text = "测试容器"))
    public static final String MENU_TITLE = "gui.testmod.container";
}
