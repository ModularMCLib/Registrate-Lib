package com.modularmc.registrate.localization

import com.modularmc.registrate.localization.terminology.Domain
import com.modularmc.registrate.localization.terminology.Terminology.GlossaryTerm

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet

/** 供本地化声明复用的默认领域、文本类型、术语和单位。 */
@JvmRecord
data class DeclarationDefaults @JvmOverloads constructor(
	val domains: List<Domain>,
	val type: TextType?,
	val glossary: List<GlossaryTerm>,
	val glossaryIds: List<String> = emptyList(),
	val unitIds: List<String> = emptyList(),
) {
	/**
	 * 按字段合并默认值。显式引入的条目优先，其次使用继承值，最后使用基础默认值。
	 * 多个引入项的文本类型或同标识术语发生冲突时，构建会抛出异常。
	 */
	class Builder {
		private var defaults = EMPTY
		private var parent = EMPTY
		private val included = ObjectArrayList<DeclarationDefaults>()

		/** 设置基础默认值。仅在引入项和继承项未提供某字段时使用。 */
		fun defaults(defaults: DeclarationDefaults) = apply { this.defaults = defaults }

		/** 设置继承来源。字段没有被引入项提供时，先使用继承值再回退到基础默认值。 */
		fun inherits(parent: DeclarationDefaults) = apply { this.parent = parent }

		/** 引入一组优先级较高的默认值。多次调用会累加引入项。 */
		fun include(vararg defaults: DeclarationDefaults) = apply { included.addAll(defaults.asList()) }

		/** 合并当前配置的默认值。同标识术语或引入的文本类型发生冲突时抛出 [IllegalArgumentException]。 */
		fun build(): DeclarationDefaults {
			val domains = ObjectLinkedOpenHashSet<Domain>()
			val glossary = Object2ObjectLinkedOpenHashMap<String, GlossaryTerm>()
			val glossaryIds = ObjectLinkedOpenHashSet<String>()
			val units = ObjectLinkedOpenHashSet<String>()
			var type: TextType? = null
			for (defaults in included) {
				domains.addAll(defaults.domains)
				glossaryIds.addAll(defaults.glossaryIds)
				units.addAll(defaults.unitIds)
				for (term in defaults.glossary) {
					val existing = glossary.putIfAbsent(term.id, term)
					require(existing == null || existing == term) { "Included defaults disagree on glossary term '${term.id}'" }
				}
				defaults.type?.let { candidate ->
					require(type == null || type == candidate) { "Included defaults disagree on text type: ${type?.id} and ${candidate.id}" }
					type = candidate
				}
			}
			val glossaryParent = if (parent.glossary.isEmpty() && parent.glossaryIds.isEmpty()) defaults else parent
			val useIncludedGlossary = glossary.isNotEmpty() || glossaryIds.isNotEmpty()
			return DeclarationDefaults(
				if (domains.isEmpty()) parent.domains.ifEmpty { defaults.domains } else ObjectArrayList(domains),
				type ?: parent.type ?: defaults.type,
				if (useIncludedGlossary) ObjectArrayList(glossary.values) else glossaryParent.glossary,
				if (useIncludedGlossary) ObjectArrayList(glossaryIds) else glossaryParent.glossaryIds,
				if (units.isEmpty()) parent.unitIds.ifEmpty { defaults.unitIds } else ObjectArrayList(units),
			)
		}
	}

	companion object {
		internal val EMPTY = DeclarationDefaults(emptyList(), null, emptyList())
	}
}
