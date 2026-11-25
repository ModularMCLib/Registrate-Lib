package com.modularmc.registrate.localization.terminology

import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TextType
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.util.Optional

/** 保存单个模组定义的术语。模组初始化时注册，解析本地化声明时查询。 */
class Terminology {
	/** 带有独立标识的一个术语义项。领域和文本类型用于消除歧义，译文、别名和关系供查询与数据生成使用。 */
	@JvmRecord
	data class GlossaryTerm @JvmOverloads constructor(
		val id: String,
		val source: String,
		val domains: List<Domain>,
		val preferredTranslations: Map<MinecraftLocale, String>,
		val textTypes: Set<TextType> = emptySet(),
		val acceptedTranslations: Map<MinecraftLocale, Set<String>> = emptyMap(),
		val forbiddenTranslations: Map<MinecraftLocale, Set<String>> = emptyMap(),
		val aliases: Set<String> = emptySet(),
		val relations: Map<TermRelation, Set<String>> = emptyMap(),
		val unitIds: Set<String> = emptySet(),
	) {
		internal fun matches(sourceText: String): Boolean = source.equals(sourceText, ignoreCase = true) || aliases.any { it.equals(sourceText, ignoreCase = true) }
	}

	/** 定义术语之间的同义、上下位、组成部分和一般关联。 */
	enum class TermRelation {
		SYNONYM,
		BROADER,
		NARROWER,
		PART_OF,
		HAS_PART,
		RELATED,
	}

	/** 记录一个单位的稳定标识、显示符号及分语言的名称和格式。 */
	@JvmRecord
	data class MeasurementUnit @JvmOverloads constructor(val id: String, val symbol: String, val names: Map<MinecraftLocale, String>, val formats: Map<MinecraftLocale, UnitFormat> = emptyMap()) {
		/** 按目标语言的注册格式显示数值和单位。缺少该语言的格式时，用一个空格连接数值与符号。 */
		fun format(value: Number, locale: MinecraftLocale): String = formats[locale]?.format(value, symbol) ?: "$value $symbol"
	}

	private val domains = ObjectOpenHashSet<Domain>()
	private val indexedGlossary = IndexedGlossary()
	private val glossaries = ObjectArrayList<Glossary>(1).apply { add(indexedGlossary) }
	private val units = Object2ObjectOpenHashMap<String, MeasurementUnit>()

	/** 登记供本地化声明引用的领域。 */
	fun register(domain: Domain) {
		domains.add(domain)
	}

	/** 将术语存入模组自带的词汇表。已有同标识术语时，同时替换旧的词形和关系索引。 */
	fun register(term: GlossaryTerm) {
		indexedGlossary.register(term)
	}

	/** 挂接外部词汇表。数据生成期间会用它查询术语、别名和关系。 */
	fun register(glossary: Glossary) {
		glossaries.add(glossary)
	}

	/** 按稳定标识登记单位。同一标识再次登记时采用新定义。 */
	fun register(unit: MeasurementUnit) {
		units[unit.id] = unit
	}

	/** 检查领域是否已在当前模组登记。 */
	fun hasDomain(domain: Domain): Boolean = domains.contains(domain)

	internal fun synonyms(term: GlossaryTerm): ObjectArrayList<GlossaryTerm> {
		val discovered = ObjectOpenHashSet<String>()
		val pending = ObjectArrayList<GlossaryTerm>(4)
		val result = ObjectArrayList<GlossaryTerm>(4)
		discovered.add(term.id)
		pending.add(term)
		result.add(term)
		var index = 0
		while (index < pending.size) {
			val current = pending[index++]
			for (glossary in glossaries) {
				for (related in glossary.findRelated(current.id, TermRelation.SYNONYM)) {
					if (discovered.add(related.id)) {
						pending.add(related)
						result.add(related)
					}
				}
			}
		}
		return result
	}

	/** 按稳定标识查询术语，没有匹配的定义时返回空值。 */
	fun glossary(id: String): Optional<GlossaryTerm> = Optional.ofNullable(findTerm(id))

	/** 按稳定标识查找显示单位，没有匹配的定义时返回空值。 */
	fun unit(id: String): Optional<MeasurementUnit> = Optional.ofNullable(units[id])

	internal fun unitSymbol(id: String): String = units.getValue(id).symbol

	internal fun resolveTerm(key: TranslationKey, declaration: LocalizationDeclaration, source: TranslationSource): GlossaryTerm? {
		if (declaration.glossary.isNotEmpty() || declaration.glossaryIds.isNotEmpty()) {
			resolveExplicitTerm(key, declaration, source)?.let { return it }
		}
		if (glossaries.size == 1 && indexedGlossary.isEmpty()) return null

		val label = declaration.source.lowercase()
		val type = declaration.type
		val domains = declaration.domains
		var best: GlossaryTerm? = null
		var other: GlossaryTerm? = null
		var bestScore = -1
		for (glossary in glossaries) {
			for (term in glossary.findBySource(label)) {
				val domainMatches = term.domains.any { it in domains }
				if (domains.isNotEmpty() && term.domains.isNotEmpty() && !domainMatches) continue
				val typeMatches = type != null && type in term.textTypes
				if (type != null && term.textTypes.isNotEmpty() && !typeMatches) continue

				val score = (if (domainMatches) 1 else 0) + (if (typeMatches) 1 else 0)
				if (score > bestScore) {
					bestScore = score
					best = term
					other = null
				} else if (score == bestScore && term.id != best?.id) {
					other = term
				}
			}
		}
		val selected = best ?: return null
		if (other != null) ambiguousTerm(key, source, selected, other)
		return selected
	}

	private fun resolveExplicitTerm(key: TranslationKey, declaration: LocalizationDeclaration, source: TranslationSource): GlossaryTerm? {
		var selected: GlossaryTerm? = null

		fun consider(term: GlossaryTerm) {
			if (!term.matches(declaration.source)) return
			val previous = selected
			if (previous != null && previous.id != term.id) ambiguousTerm(key, source, previous, term)
			selected = term
		}

		for (term in declaration.glossary) consider(term)
		for (id in declaration.glossaryIds) {
			val term = requireNotNull(findTerm(id)) { "Unknown glossary term '$id' for $key at ${source.location}" }
			consider(term)
		}
		return selected
	}

	private fun findTerm(id: String): GlossaryTerm? = glossaries.firstNotNullOfOrNull { it.find(id) }

	private fun ambiguousTerm(key: TranslationKey, source: TranslationSource, first: GlossaryTerm, second: GlossaryTerm): Nothing =
		throw IllegalArgumentException("Ambiguous glossary term for $key at ${source.location}: ${first.id} or ${second.id}")

	internal companion object {
		val EMPTY = Terminology()
	}
}
