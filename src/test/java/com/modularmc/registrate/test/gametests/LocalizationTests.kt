package com.modularmc.registrate.test.gametests

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.localization.DeclarationDefaults
import com.modularmc.registrate.localization.ImportedTranslation
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.LocalizationDeclaration.Companion.sourceText
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.MinecraftLocale.EN_AU
import com.modularmc.registrate.localization.MinecraftLocale.EN_GB
import com.modularmc.registrate.localization.MinecraftLocale.EN_UD
import com.modularmc.registrate.localization.MinecraftLocale.EN_US
import com.modularmc.registrate.localization.MinecraftLocale.ZH_CN
import com.modularmc.registrate.localization.SourceRevision
import com.modularmc.registrate.localization.TextType
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.localization.TranslationStatus
import com.modularmc.registrate.localization.TranslationStatus.APPROVED
import com.modularmc.registrate.localization.TranslationStatus.DRAFT
import com.modularmc.registrate.localization.TranslationStatus.IN_REVIEW
import com.modularmc.registrate.localization.TranslationStatus.STALE
import com.modularmc.registrate.localization.index.IndexedTranslations
import com.modularmc.registrate.localization.locale.LocaleDerivation
import com.modularmc.registrate.localization.resolution.TranslationRegistry
import com.modularmc.registrate.localization.resolution.TranslationRegistry.LanguageFile
import com.modularmc.registrate.localization.terminology.Domain
import com.modularmc.registrate.localization.terminology.Glossary
import com.modularmc.registrate.localization.terminology.Terminology
import com.modularmc.registrate.localization.terminology.Terminology.GlossaryTerm
import com.modularmc.registrate.localization.terminology.Terminology.MeasurementUnit
import com.modularmc.registrate.localization.terminology.Terminology.TermRelation
import com.modularmc.registrate.localization.terminology.UnitFormat
import com.modularmc.registrate.providers.RegistrateLangProvider
import com.modularmc.registrate.providers.core.LanguageOutput
import com.modularmc.registrate.test.mod.TestMod
import com.modularmc.registrate.test.mod.localization.ChineseLocaleTestMod
import com.modularmc.registrate.test.mod.localization.EnglishLabels

import net.minecraft.data.PackOutput
import net.neoforged.testframework.DynamicTest
import net.neoforged.testframework.annotation.ForEachTest
import net.neoforged.testframework.annotation.TestHolder
import net.neoforged.testframework.gametest.EmptyTemplate
import net.neoforged.testframework.gametest.GameTest

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
			val terms = mod.registrate().terminology()
			helper.assertValueEqual(listOf(Domain.PHYSICS), metadata.domains, "Object-level domain overrides the default")
			helper.assertTrue(metadata.type == TextType.ITEM, "Default text type")
			helper.assertValueEqual(terms.glossary("electric_potential").orElseThrow(), metadata.glossary.first(), "Default glossary term")
			helper.assertValueEqual("V", terms.unit("volt").orElseThrow().symbol, "Unit symbol")
			helper.succeed()
		}
	}

	@JvmStatic
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(description = ["Resolve glossary labels using their owning mod, context and explicit terminology"])
	fun resolveGlossaryByContext(test: DynamicTest) {
		test.onGameTest { helper ->
			val terms = Terminology()
			terms.register(GlossaryTerm("electric_potential", "potential", listOf(Domain.PHYSICS), mapOf(ZH_CN to "电势"), setOf(TextType.GUI_LABEL)))
			val personal = GlossaryTerm("personal_potential", "potential", emptyList(), mapOf(ZH_CN to "潜力"), setOf(TextType.DESCRIPTION))
			terms.register(object : Glossary {
				override fun find(id: String): GlossaryTerm? = personal.takeIf { it.id == id }

				override fun findBySource(normalizedSource: String): Iterable<GlossaryTerm> = if (normalizedSource == personal.source) listOf(personal) else emptyList()
			})
			val otherTerms = Terminology()
			otherTerms.register(GlossaryTerm("potential", "potential", emptyList(), mapOf(ZH_CN to "势能")))

			val registry = TranslationRegistry()
			val electrical = TranslationKey(TestMod.MOD_ID, "gui.testmod.electrical_potential")
			val talent = electrical.copy(value = "gui.testmod.personal_potential")
			val override = electrical.copy(value = "gui.testmod.potential_override")
			val otherMod = electrical.copy(value = "gui.localetest.potential")
			registry.add(electrical, EN_US, LocalizationDeclaration.Builder().source("Potential").domain(Domain.PHYSICS).build(), builder, terms)
			registry.add(talent, EN_US, LocalizationDeclaration.Builder().source("Potential").type(TextType.DESCRIPTION).build(), manual, terms)
			val explicit = LocalizationDeclaration.Builder().source("Potential").domain(Domain.PHYSICS).glossaryIds("personal_potential").build()
			registry.add(override, EN_US, explicit, manual, terms)
			registry.add(otherMod, EN_US, sourceText("Potential"), manual, otherTerms)
			val languages = registry.resolve()
			helper.assertValueEqual("电势", languages.text(ZH_CN, electrical.value), "Domain selects electric potential")
			helper.assertValueEqual("潜力", languages.text(ZH_CN, talent.value), "Text type selects personal potential")
			helper.assertValueEqual("潜力", languages.text(ZH_CN, override.value), "Explicit glossary ID takes priority")
			helper.assertValueEqual("势能", languages.text(ZH_CN, otherMod.value), "A shared namespace uses its declaring mod's glossary")

			registry.add(electrical, EN_US, LocalizationDeclaration.Builder().source("Potential").translation(ZH_CN, "电位").build(), manual)
			helper.assertValueEqual("电位", registry.resolve().text(ZH_CN, electrical.value), "Explicit translation takes priority over glossary")

			registry.add(inventory, EN_US, sourceText("Potential"), manual, terms)
			val diagnostic = failure(registry::resolve)
			helper.assertTrue(listOf("electric_potential", "personal_potential", manual.location, inventory.value).all { it in diagnostic }, "Ambiguous term diagnostic")
			helper.succeed()
		}
	}

	@JvmStatic
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(description = ["Combine declaration defaults and format a unit for its target locale"])
	fun combineDefaultsAndFormatUnits(test: DynamicTest) {
		test.onGameTest { helper ->
			val terms = Terminology()
			val ohm = MeasurementUnit("ohm", "Ω", mapOf(EN_US to "ohm"), mapOf(ZH_CN to UnitFormat("0.00E0", '.', "")))
			terms.register(ohm)
			helper.assertValueEqual("1250 Ω", ohm.format(1250, EN_US), "Default unit formatting")
			helper.assertValueEqual("1.25E3Ω", ohm.format(1250, ZH_CN), "Localized scientific formatting")
			val power = MeasurementUnit("kilowatt", "kW", emptyMap(), mapOf(ZH_CN to UnitFormat("0.0", ',', " ")))
			helper.assertValueEqual("2,5 kW", power.format(2.5, ZH_CN), "Configured decimal separator")

			val resistance = GlossaryTerm(
				"resistance",
				"electrical resistance",
				listOf(Domain.PHYSICS),
				mapOf(ZH_CN to "电阻"),
				aliases = setOf("resistance"),
				relations = mapOf(TermRelation.BROADER to setOf("electrical_quantity")),
				unitIds = setOf("ohm"),
			)
			terms.register(resistance)
			val defaults = DeclarationDefaults(listOf(Domain.ENGINEERING), TextType.DESCRIPTION, emptyList(), unitIds = listOf("kilowatt"))
			val parent = DeclarationDefaults(listOf(Domain.PHYSICS), TextType.GUI_LABEL, emptyList())
			val terminology = DeclarationDefaults(emptyList(), null, emptyList(), glossaryIds = listOf(resistance.id))
			val combined = DeclarationDefaults.Builder().defaults(defaults).inherits(parent).include(terminology).build()
			val declaration = LocalizationDeclaration.Builder().source("Resistance").defaults(combined).units("ohm").build()
			helper.assertValueEqual(listOf(Domain.PHYSICS), declaration.domains, "Parent domains override defaults")
			helper.assertValueEqual(TextType.GUI_LABEL, declaration.type!!, "Parent text type")
			helper.assertValueEqual(listOf("kilowatt"), combined.unitIds, "Unit default")
			helper.assertValueEqual(listOf("ohm"), declaration.unitIds, "Declaration overrides unit defaults")
			helper.assertValueEqual(setOf("electrical_quantity"), terms.glossary("resistance").orElseThrow().relations.getValue(TermRelation.BROADER), "Glossary relationships")
			val registry = TranslationRegistry()
			registry.add(inventory, EN_US, declaration, manual, terms)
			helper.assertValueEqual("电阻", registry.resolve().text(ZH_CN, inventory.value), "Included glossary ID resolves its alias")
			val conflict = failure { DeclarationDefaults.Builder().include(parent, defaults).build() }
			helper.assertTrue("GUI_LABEL" in conflict && "DESCRIPTION" in conflict, "Same-level text types conflict")
			helper.succeed()
		}
	}

	@JvmStatic
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(description = ["Derive configured locales while preserving Minecraft text and measurement symbols"])
	fun deriveLocalesAndProtectFormatting(test: DynamicTest) {
		test.onGameTest { helper ->
			val owner = UnconfiguredRegistrate()
			val terms = owner.terminology()
			terms.register(MeasurementUnit("kilowatt", "kW", emptyMap()))
			terms.register(MeasurementUnit("ohm", "Ω", emptyMap()))
			owner.deriveLocale(LocaleDerivation(EN_US, EN_GB) { text, _ -> text.lowercase().replace("color", "colour") })
			owner.deriveLocale(LocaleDerivation.fallback(EN_GB, EN_AU))

			val registry = TranslationRegistry()
			owner.configureLocaleDerivations(registry)
			val text = $$"§cColor %1$s at 5 kW / Ω\nhttps://example.org/Power %%"
			val british = $$"§ccolour %1$s at 5 kW / Ω\nhttps://example.org/Power %%"
			val declaration = LocalizationDeclaration.Builder().source(text).units("kilowatt", "ohm").build()
			registry.add(inventory, EN_US, declaration, manual, terms)
			val languages = registry.resolve()
			helper.assertValueEqual(british, languages.text(EN_GB, inventory.value), "Only plain text is converted")
			helper.assertValueEqual(british, languages.text(EN_AU, inventory.value), "An explicitly configured chain can use a derived text")
			val inverted = languages.text(EN_UD, inventory.value)
			val preserved = listOf($$"%1$s", "%%", "kW", "Ω", "https://example.org/Power", "\n")
			helper.assertTrue(inverted.startsWith("§c") && preserved.all { it in inverted }, "Upside-down English protects formatting and units")

			val explicit = $$"Color %1$s %%"
			registry.add(inventory, EN_US, declaration.copy(translations = mapOf(EN_GB to explicit)), builder, terms)
			val explicitLanguages = registry.resolve()
			helper.assertValueEqual(explicit, explicitLanguages.text(EN_GB, inventory.value), "An explicit translation wins")
			helper.assertValueEqual(explicit, explicitLanguages.text(EN_AU, inventory.value), "The chain uses the winning text")

			owner.deriveLocale(LocaleDerivation.fallback(EN_GB, EN_US))
			val cycle = failure { owner.configureLocaleDerivations(registry) }
			helper.assertTrue(listOf(owner.modid, "en_us", "en_gb", "cycle").all { it in cycle }, "Cycle diagnostic")
			helper.succeed()
		}
	}

	@JvmStatic
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(description = ["Resolve imported translations by source revision, review state and lock state"])
	fun resolveImportedTranslations(test: DynamicTest) {
		test.onGameTest { helper ->
			val registry = TranslationRegistry()
			val owner = UnconfiguredRegistrate(TestMod.MOD_ID).sourceLocale(EN_US)
			val terms = owner.terminology()
			val reviewedKey = inventory.copy(value = "gui.testmod.reviewed")
			val staleKey = inventory.copy(value = "gui.testmod.stale")
			val lockedKey = inventory.copy(value = "gui.testmod.locked")
			val sharedKey = inventory.copy(value = "gui.testmod.shared")
			val annotatedKey = inventory.copy(value = EnglishLabels.MENU_TITLE)

			val current = "Current source"
			val previous = "Previous source"
			registry.add(reviewedKey, EN_US, sourceText(current), manual, terms)
			registry.add(staleKey, EN_US, sourceText(previous), TranslationSource(Kind.AUTOMATIC, "Default label"), terms)
			registry.add(lockedKey, EN_US, sourceText(current), manual, terms)
			registry.add(sharedKey, EN_US, sourceText(previous), TranslationSource(Kind.AUTOMATIC, "Default shared label"), terms)
			registry.add(sharedKey, EN_US, sourceText("Another mod source"), manual, Terminology())

			fun importText(key: TranslationKey, text: String, source: String, status: TranslationStatus = APPROVED, locked: Boolean = false) =
				ImportedTranslation(key, ZH_CN, text, SourceRevision.of(source), status, locked, "translations/${key.value}").also {
					owner.importTranslation(it)
				}

			importText(reviewedKey, "审核中", current, IN_REVIEW)
			importText(reviewedKey, "已审核", current)
			importText(reviewedKey, "草稿", current, DRAFT)
			val stale = importText(staleKey, "旧译文", previous)
			val locked = importText(lockedKey, "锁定旧译文", previous, locked = true)
			val shared = importText(sharedKey, "共享译文", previous)
			importText(lockedKey, "新译文", current)
			importText(annotatedKey, "导入译文", "Test Container", locked = true)

			RegistrateLangProvider(owner, PackOutput(Path.of("unused-language-output"))).collect(registry)
			helper.assertValueEqual(stale.text, registry.resolve().text(ZH_CN, staleKey.value), "A matching source revision enables the import")
			registry.add(staleKey, EN_US, sourceText("Updated source"), manual, terms)

			val imports = ArrayList<ImportedTranslation>()
			val languages = registry.resolve(imports::add)
			val chinese = languages.getValue(LanguageFile(TestMod.MOD_ID, ZH_CN))
			helper.assertValueEqual("已审核", chinese.getValue(reviewedKey.value), "Approved translation outranks lower review states")
			helper.assertTrue(stale.copy(status = STALE) in imports, "Source change marks the import stale and keeps its provenance")
			helper.assertTrue(staleKey.value !in chinese, "Unlocked stale text is excluded from language output")
			helper.assertTrue(locked.copy(status = STALE) in imports, "A locked import still reports its stale revision")
			helper.assertValueEqual(locked.text, chinese.getValue(lockedKey.value), "The locked import preserves its wording")
			helper.assertValueEqual(shared.text, chinese.getValue(sharedKey.value), "The import uses its own source when another mod has a higher-priority declaration for the same key")
			helper.assertValueEqual("测试容器", chinese.getValue(annotatedKey.value), "The annotation's explicit translation takes priority")
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

	private class UnconfiguredRegistrate(modId: String = "missing_locale") : AbstractRegistrate<UnconfiguredRegistrate>(modId)
}
