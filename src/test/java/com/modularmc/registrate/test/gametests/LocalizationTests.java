package com.modularmc.registrate.test.gametests;

import com.modularmc.registrate.AbstractRegistrate;
import com.modularmc.registrate.localization.LocalizationDeclaration;
import com.modularmc.registrate.localization.MinecraftLocale;
import com.modularmc.registrate.localization.TextType;
import com.modularmc.registrate.localization.TranslationKey;
import com.modularmc.registrate.localization.TranslationSource;
import com.modularmc.registrate.localization.TranslationSource.Kind;
import com.modularmc.registrate.localization.resolution.TranslationRegistry;
import com.modularmc.registrate.localization.resolution.TranslationRegistry.LanguageFile;
import com.modularmc.registrate.localization.terminology.Domain;
import com.modularmc.registrate.test.mod.TestMod;

import net.minecraft.gametest.framework.GameTest;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

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
            var label = new LocalizationDeclaration.Builder().source("%s at %2$s (%s%%)")
                    .translation(MinecraftLocale.ZH_CN, "%2$s 上的 %1$s（%2$s%%）").build();
            registry.add(INVENTORY, MinecraftLocale.EN_US, label, BUILDER);
            registry.add(INVENTORY, MinecraftLocale.EN_US, LocalizationDeclaration.sourceText(label.source()), MANUAL);

            var languages = registry.resolve();
            helper.assertValueEqual(label.source(), languages.get(new LanguageFile(TestMod.MOD_ID, MinecraftLocale.EN_US)).get(INVENTORY.value()), "Declared source");
            helper.assertValueEqual(label.translations().get(MinecraftLocale.ZH_CN), languages.get(new LanguageFile(TestMod.MOD_ID, MinecraftLocale.ZH_CN)).get(INVENTORY.value()), "Reordered indexed arguments");
            helper.assertTrue(languages.get(new LanguageFile(TestMod.MOD_ID, MinecraftLocale.EN_UD)).get(INVENTORY.value()).contains("%%%2$s"), "Variant reverses the percent and argument tokens as units");

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
