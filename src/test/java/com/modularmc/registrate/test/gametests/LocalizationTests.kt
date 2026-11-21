package com.modularmc.registrate.test.gametests

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.LocalizationDeclaration.Companion.sourceText
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.MinecraftLocale.EN_UD
import com.modularmc.registrate.localization.MinecraftLocale.EN_US
import com.modularmc.registrate.localization.MinecraftLocale.ZH_CN
import com.modularmc.registrate.localization.TextType
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.localization.index.IndexedTranslations
import com.modularmc.registrate.localization.resolution.TranslationRegistry
import com.modularmc.registrate.localization.resolution.TranslationRegistry.LanguageFile
import com.modularmc.registrate.localization.terminology.Domain
import com.modularmc.registrate.providers.core.LanguageOutput
import com.modularmc.registrate.test.mod.TestMod
import com.modularmc.registrate.test.mod.localization.ChineseLocaleTestMod
import com.modularmc.registrate.test.mod.localization.EnglishLabels

import net.minecraft.data.PackOutput
import net.minecraft.gametest.framework.GameTest
import net.neoforged.testframework.DynamicTest
import net.neoforged.testframework.annotation.ForEachTest
import net.neoforged.testframework.annotation.TestHolder
import net.neoforged.testframework.gametest.EmptyTemplate

import java.nio.file.Path

@ForEachTest(groups = ["registrate-localization"])
object LocalizationTests {
	private val inventory = TranslationKey(TestMod.MOD_ID, "gui.testmod.inventory")
	private val builder = TranslationSource(Kind.DECLARED, "Builder testmod:inventory")
	private val manual = TranslationSource(Kind.DECLARED, "Manual inventory label")

	@JvmStatic
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(description = ["Use explicit translations and preserve reordered Minecraft placeholders"])
	fun resolveDeclaredTextAndMinecraftPlaceholders(test: DynamicTest) {
		test.onGameTest { helper ->
			val registry = TranslationRegistry()
			registry.add(inventory, EN_US, sourceText("Automatic Name"), TranslationSource(Kind.AUTOMATIC, "Default inventory label"))
			registry.add(inventory, EN_US, sourceText("Other Automatic Name"), TranslationSource(Kind.AUTOMATIC, "Other default inventory label"))
			val label = LocalizationDeclaration.Builder()
				.source($$"%s at %2$s (%s%%)")
				.translation(ZH_CN, $$"%2$s 上的 %1$s（%2$s%%）")
				.build()
			registry.add(inventory, EN_US, label, builder)
			registry.add(inventory, EN_US, sourceText(label.source), manual)

			val languages = registry.resolve()
			helper.assertValueEqual(label.source, languages.text(EN_US, inventory.value), "Declared source")
			helper.assertValueEqual(label.translations.getValue(ZH_CN), languages.text(ZH_CN, inventory.value), "Reordered indexed arguments")
			helper.assertTrue($$"%%%2$s" in languages.text(EN_UD, inventory.value), "Variant reverses the percent and argument tokens as units")

			val explicitVariant = $$"%1$s / %2$s / %2$s %%"
			registry.add(
				inventory,
				EN_US,
				LocalizationDeclaration.Builder().source(label.source).translation(EN_UD, explicitVariant).build(),
				manual,
			)
			helper.assertValueEqual(explicitVariant, registry.resolve().text(EN_UD, inventory.value), "Explicit variant takes priority")

			val mod = TestMod.instance()
			val metadata = mod.testitem.metadata().localization()!!.declaration.get()
			val catalog = mod.registrate().localizationCatalog()
			helper.assertValueEqual(listOf(Domain.PHYSICS), metadata.domains, "Object-level domain overrides the profile")
			helper.assertTrue(metadata.type == TextType.ITEM, "Text type from profile")
			helper.assertValueEqual(catalog.glossary("electric_potential").orElseThrow(), metadata.glossary.first(), "Glossary from profile")
			helper.assertValueEqual("V", catalog.unit("volt").orElseThrow().symbol, "Unit symbol")
			helper.succeed()
		}
	}

	@JvmStatic
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(description = ["Report both conflicting declarations, mismatched placeholders and missing source locale"])
	fun locateInvalidLocalizationDeclarations(test: DynamicTest) {
		test.onGameTest { helper ->
			val conflict = TranslationRegistry()
			conflict.add(inventory, EN_US, sourceText("First"), builder)
			conflict.add(inventory, EN_US, sourceText("Second"), manual)
			val conflictingDeclarations = failure(conflict::resolve)
			helper.assertTrue(builder.location in conflictingDeclarations && manual.location in conflictingDeclarations, "Both conflicting sources")

			val wrongFormat = TranslationRegistry()
			wrongFormat.add(
				inventory,
				EN_US,
				LocalizationDeclaration.Builder().source("Inventory: %s").translation(ZH_CN, $$"物品栏：%2$s").build(),
				manual,
			)
			val mismatch = failure(wrongFormat::resolve)
			helper.assertTrue("placeholder mismatch" in mismatch && manual.location in mismatch, "Translation that changed its source argument")
			helper.assertTrue("[missing_locale]" in failure(UnconfiguredRegistrate()::requireSourceLocale), "Mod with missing source locale")
			helper.succeed()
		}
	}

	@JvmStatic
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(description = ["Read Java and Kotlin indexes and fail before writing when a manual declaration conflicts with an annotation"])
	fun collectCompiledAnnotationsBeforeWriting(test: DynamicTest) {
		test.onGameTest { helper ->
			val registry = TranslationRegistry()
			IndexedTranslations.collect(TestMod.MOD_ID, EN_US, registry)
			IndexedTranslations.collect(ChineseLocaleTestMod.MOD_ID, ZH_CN, registry)
			val languages = registry.resolve()
			helper.assertValueEqual("Test", languages.text(EN_US, TestMod.CUSTOM_LANG), "Java annotation")
			helper.assertValueEqual("Test Container", languages.text(EN_US, EnglishLabels.MENU_TITLE), "Kotlin field annotation")
			helper.assertValueEqual("Low power", languages.text(EN_US, "gui.testmod.power.low"), "First variant")
			helper.assertValueEqual("高功率", languages.text(ZH_CN, "gui.testmod.power.high"), "Second variant's target text")
			helper.assertValueEqual($$"频率：%1$s Hz", languages.text(ZH_CN, "gui.localetest.frequency"), "Chinese source in shared namespace")

			registry.add(TranslationKey(TestMod.MOD_ID, EnglishLabels.MENU_TITLE), EN_US, sourceText("Conflicting name"), manual)
			val diagnostic = failure {
				LanguageOutput.write({ _, _, _ ->
					throw AssertionError("Conflicting declarations caused a language write")
				}, PackOutput(Path.of("unused-language-output")), registry)
			}
			helper.assertTrue("@Trans ${EnglishLabels::class.java.name}#MENU_TITLE" in diagnostic, "Annotated field in conflict diagnostic")
			helper.assertTrue(manual.location in diagnostic, "Manual declaration in conflict diagnostic")
			helper.succeed()
		}
	}

	private fun Map<LanguageFile, Map<String, String>>.text(locale: MinecraftLocale, key: String): String = getValue(LanguageFile(TestMod.MOD_ID, locale)).getValue(key)

	private fun failure(operation: () -> Unit): String {
		try {
			operation()
		} catch (error: IllegalArgumentException) {
			return error.message!!
		}
		throw AssertionError("Localization operation should have failed")
	}

	private class UnconfiguredRegistrate : AbstractRegistrate<UnconfiguredRegistrate>("missing_locale")
}
