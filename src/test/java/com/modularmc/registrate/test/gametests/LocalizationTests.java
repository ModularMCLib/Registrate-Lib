package com.modularmc.registrate.test.gametests;

import com.modularmc.registrate.AbstractRegistrate;
import com.modularmc.registrate.localization.LocalizationDeclaration;
import com.modularmc.registrate.localization.MinecraftLocale;
import com.modularmc.registrate.localization.TextType;
import com.modularmc.registrate.localization.TranslationKey;
import com.modularmc.registrate.localization.TranslationSource;
import com.modularmc.registrate.localization.TranslationSource.Kind;
import com.modularmc.registrate.localization.index.IndexedTranslations;
import com.modularmc.registrate.localization.resolution.TranslationRegistry;
import com.modularmc.registrate.localization.resolution.TranslationRegistry.LanguageFile;
import com.modularmc.registrate.localization.terminology.Domain;
import com.modularmc.registrate.providers.core.LanguageOutput;
import com.modularmc.registrate.test.mod.TestMod;
import com.modularmc.registrate.test.mod.localization.ChineseLocaleTestMod;
import com.modularmc.registrate.test.mod.localization.EnglishLabels;

import net.minecraft.data.PackOutput;
import net.minecraft.gametest.framework.GameTest;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.nio.file.Path;
import java.util.List;

@ForEachTest(groups = "registrate-localization")
public class LocalizationTests {

    private static final TranslationKey INVENTORY = new TranslationKey(TestMod.MOD_ID, "gui.testmod.inventory");
    private static final TranslationSource BUILDER = new TranslationSource(Kind.DECLARED, "Builder testmod:inventory");
    private static final TranslationSource MANUAL = new TranslationSource(Kind.DECLARED, "Manual inventory label");

    @GameTest
    @EmptyTemplate(floor = true)
    @TestHolder(description = "Use explicit translations and preserve reordered Minecraft placeholders")
    public static void resolveDeclaredTextAndMinecraftPlaceholders(DynamicTest test) {
        test.onGameTest(helper -> {
            var registry = new TranslationRegistry();
            registry.add(INVENTORY, MinecraftLocale.EN_US, LocalizationDeclaration.sourceText("Automatic Name"),
                    new TranslationSource(Kind.AUTOMATIC, "Default inventory label"));
            registry.add(INVENTORY, MinecraftLocale.EN_US, LocalizationDeclaration.sourceText("Other Automatic Name"),
                    new TranslationSource(Kind.AUTOMATIC, "Other default inventory label"));
            var label = new LocalizationDeclaration.Builder().source("%s at %2$s (%s%%)")
                    .translation(MinecraftLocale.ZH_CN, "%2$s 上的 %1$s（%2$s%%）").build();
            registry.add(INVENTORY, MinecraftLocale.EN_US, label, BUILDER);
            registry.add(INVENTORY, MinecraftLocale.EN_US, LocalizationDeclaration.sourceText(label.source()), MANUAL);

            var languages = registry.resolve();
            helper.assertValueEqual(label.source(), languages.get(new LanguageFile(TestMod.MOD_ID, MinecraftLocale.EN_US)).get(INVENTORY.value()), "Declared source");
            helper.assertValueEqual(label.translations().get(MinecraftLocale.ZH_CN), languages.get(new LanguageFile(TestMod.MOD_ID, MinecraftLocale.ZH_CN)).get(INVENTORY.value()), "Reordered indexed arguments");
            helper.assertTrue(languages.get(new LanguageFile(TestMod.MOD_ID, MinecraftLocale.EN_UD)).get(INVENTORY.value()).contains("%%%2$s"), "Variant reverses the percent and argument tokens as units");

            String explicitVariant = "%1$s / %2$s / %2$s %%";
            registry.add(INVENTORY, MinecraftLocale.EN_US, new LocalizationDeclaration.Builder().source(label.source()).translation(MinecraftLocale.EN_UD, explicitVariant).build(), MANUAL);
            helper.assertValueEqual(explicitVariant, registry.resolve().get(new LanguageFile(TestMod.MOD_ID, MinecraftLocale.EN_UD)).get(INVENTORY.value()), "Explicit variant takes priority after adding a declaration");

            var metadata = TestMod.instance().testitem.metadata().localization().declaration().get();
            var catalog = TestMod.instance().registrate().localizationCatalog();
            helper.assertValueEqual(List.of(Domain.PHYSICS), metadata.domains(), "Object-level domain overrides the profile");
            helper.assertValueEqual(TextType.ITEM, metadata.type(), "Text type from profile");
            helper.assertValueEqual(catalog.glossary("electric_potential").orElseThrow(), metadata.glossary().getFirst(), "Glossary from profile");
            helper.assertValueEqual("V", catalog.unit("volt").orElseThrow().symbol(), "Unit symbol");
            helper.succeed();
        });
    }

    @GameTest
    @EmptyTemplate(floor = true)
    @TestHolder(description = "Report both conflicting declarations, mismatched placeholders and missing source locale")
    public static void locateInvalidLocalizationDeclarations(DynamicTest test) {
        test.onGameTest(helper -> {
            var conflict = new TranslationRegistry();
            conflict.add(INVENTORY, MinecraftLocale.EN_US, LocalizationDeclaration.sourceText("First"), BUILDER);
            conflict.add(INVENTORY, MinecraftLocale.EN_US, LocalizationDeclaration.sourceText("Second"), MANUAL);
            String conflictingDeclarations = failure(conflict::resolve);
            helper.assertTrue(conflictingDeclarations.contains(BUILDER.location()) && conflictingDeclarations.contains(MANUAL.location()), "Both conflicting sources");

            var wrongFormat = new TranslationRegistry();
            wrongFormat.add(INVENTORY, MinecraftLocale.EN_US,
                    new LocalizationDeclaration.Builder().source("Inventory: %s").translation(MinecraftLocale.ZH_CN, "物品栏：%2$s").build(), MANUAL);
            String mismatch = failure(wrongFormat::resolve);
            helper.assertTrue(mismatch.contains("placeholder mismatch") && mismatch.contains(MANUAL.location()), "Translation that changed its source argument");

            var missing = new UnconfiguredRegistrate();
            helper.assertTrue(failure(missing::requireSourceLocale).contains("[missing_locale]"), "Mod with missing source locale");
            helper.succeed();
        });
    }

    @GameTest
    @EmptyTemplate(floor = true)
    @TestHolder(description = "Read both mod indexes and fail before writing when a manual declaration conflicts with an annotation")
    public static void collectCompiledAnnotationsBeforeWriting(DynamicTest test) {
        test.onGameTest(helper -> {
            var registry = new TranslationRegistry();
            IndexedTranslations.collect(TestMod.MOD_ID, MinecraftLocale.EN_US, registry);
            IndexedTranslations.collect(ChineseLocaleTestMod.MOD_ID, MinecraftLocale.ZH_CN, registry);
            var languages = registry.resolve();
            var english = languages.get(new LanguageFile(TestMod.MOD_ID, MinecraftLocale.EN_US));
            var chinese = languages.get(new LanguageFile(TestMod.MOD_ID, MinecraftLocale.ZH_CN));
            helper.assertValueEqual("Test Container", english.get(EnglishLabels.MENU_TITLE), "Single field annotation");
            helper.assertValueEqual("Low power", english.get("gui.testmod.power.low"), "First variant");
            helper.assertValueEqual("高功率", chinese.get("gui.testmod.power.high"), "Second variant's target text");
            helper.assertValueEqual("频率：%1$s Hz", chinese.get("gui.localetest.frequency"), "Chinese source in shared namespace");

            registry.add(new TranslationKey(TestMod.MOD_ID, EnglishLabels.MENU_TITLE), MinecraftLocale.EN_US, LocalizationDeclaration.sourceText("Conflicting name"), MANUAL);
            var diagnostic = failure(() -> LanguageOutput.write((file, data, hash) -> {
                throw new AssertionError("Conflicting declarations caused a language write");
            }, new PackOutput(Path.of("unused-language-output")), registry));
            helper.assertTrue(diagnostic.contains("@Trans " + EnglishLabels.class.getName() + "#MENU_TITLE"), "Annotated field in conflict diagnostic");
            helper.assertTrue(diagnostic.contains(MANUAL.location()), "Manual declaration in conflict diagnostic");
            helper.succeed();
        });
    }

    private static String failure(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException error) {
            return error.getMessage();
        }
        throw new AssertionError("Localization operation should have failed");
    }

    private static final class UnconfiguredRegistrate extends AbstractRegistrate<UnconfiguredRegistrate> {

        private UnconfiguredRegistrate() {
            super("missing_locale");
        }
    }
}
