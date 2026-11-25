package com.modularmc.registrate.localization

import com.modularmc.registrate.localization.terminology.Domain
import com.modularmc.registrate.localization.terminology.Terminology.GlossaryTerm

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

/** 声明源文及显式提供的译文。数据生成时以所属模组的源语言解释源文。 */
@JvmRecord
data class LocalizationDeclaration @JvmOverloads constructor(
	val source: String,
	val translations: Map<MinecraftLocale, String>,
	val domains: List<Domain>,
	val type: TextType?,
	val glossary: List<GlossaryTerm>,
	val glossaryIds: List<String> = emptyList(),
	val unitIds: List<String> = emptyList(),
) {
	/** 为单条本地化声明收集源文、译文及可选的术语信息。构建前须设置源文。 */
	class Builder {
		private lateinit var source: String
		private var translations: Object2ObjectOpenHashMap<MinecraftLocale, String>? = null
		private var domains: List<Domain>? = null
		private var type: TextType? = null
		private var glossary: List<GlossaryTerm>? = null
		private var glossaryIds: List<String>? = null
		private var unitIds: List<String>? = null
		private var defaults = DeclarationDefaults.EMPTY

		/** 设置源语言文本，其中的占位符应和各译文对应。 */
		fun source(source: String) = apply { this.source = source }

		/** 设置声明专用文本类型。传入 `null` 时沿用构建时可用的默认类型。 */
		fun type(type: TextType?) = apply { this.type = type }

		/** 设置声明级默认值。显式字段优先，缺失字段可以回退到模组默认值。 */
		fun defaults(defaults: DeclarationDefaults) = apply { this.defaults = defaults }

		/** 设置指定语言的显式译文。同一语言重复设置时采用最后一次的内容。 */
		fun translation(locale: MinecraftLocale, text: String) = apply {
			val texts = translations ?: Object2ObjectOpenHashMap<MinecraftLocale, String>(2).also { translations = it }
			texts[locale] = text
		}

		/** 设置声明的领域列表。传入零个领域会清除继承的领域。 */
		fun domain(vararg domains: Domain) = apply { this.domains = domains.toList() }

		/** 直接指定声明使用的术语，并替换此前指定的术语标识。 */
		fun glossary(vararg terms: GlossaryTerm) = apply {
			glossary = terms.asList()
			glossaryIds = emptyList()
		}

		/** 按稳定标识引用已登记的术语，替换此前直接指定的术语。 */
		fun glossaryIds(vararg ids: String) = apply {
			glossary = emptyList()
			glossaryIds = ids.asList()
		}

		/** 指定声明使用的单位标识。派生译文时可以保留这些单位的符号。 */
		fun units(vararg ids: String) = apply { unitIds = ids.asList() }

		/** 仅使用此构建器的配置和默认值创建声明。尚未调用 [source] 时抛出 [UninitializedPropertyAccessException]。 */
		fun build(): LocalizationDeclaration = build(DeclarationDefaults.EMPTY)

		/** 创建声明，并按显式设置、声明默认值、模组默认值的顺序取各字段。尚未设置源文时抛出 [UninitializedPropertyAccessException]。 */
		fun build(moduleDefaults: DeclarationDefaults): LocalizationDeclaration {
			val entryGlossary = if (defaults.glossary.isEmpty() && defaults.glossaryIds.isEmpty()) moduleDefaults else defaults
			return LocalizationDeclaration(
				source,
				translations?.toMap() ?: emptyMap(),
				domains ?: defaults.domains.ifEmpty { moduleDefaults.domains },
				type ?: defaults.type ?: moduleDefaults.type,
				glossary ?: entryGlossary.glossary,
				glossaryIds ?: entryGlossary.glossaryIds,
				unitIds ?: defaults.unitIds.ifEmpty { moduleDefaults.unitIds },
			)
		}
	}

	companion object {
		/** 创建仅含源文的声明，供没有单独配置译文、术语或单位的条目使用。 */
		@JvmStatic
		fun sourceText(source: String) = LocalizationDeclaration(source, emptyMap(), emptyList(), null, emptyList())

		/** 用源文和指定的默认元数据创建声明。目标语言可在数据生成期间由导入或派生规则提供。 */
		@JvmStatic
		fun sourceText(source: String, defaults: DeclarationDefaults) =
			LocalizationDeclaration(source, emptyMap(), defaults.domains, defaults.type, defaults.glossary, defaults.glossaryIds, defaults.unitIds)
	}
}
