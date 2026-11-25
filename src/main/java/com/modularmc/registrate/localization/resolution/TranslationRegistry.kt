package com.modularmc.registrate.localization.resolution

import com.modularmc.registrate.internal.util.RegistrateLogger.datagen
import com.modularmc.registrate.localization.ImportedTranslation
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.SourceRevision
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.localization.TranslationStatus.STALE
import com.modularmc.registrate.localization.export.LocalizationDocument
import com.modularmc.registrate.localization.locale.LocaleDerivation
import com.modularmc.registrate.localization.locale.LocaleDerivationGraph
import com.modularmc.registrate.localization.terminology.Terminology
import com.modularmc.registrate.localization.terminology.Terminology.GlossaryTerm

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectMap
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap

import java.util.TreeMap
import java.util.function.Consumer

/** 在语言文件写出前，为同一份数据生成输出解析本地化声明。 */
internal class TranslationRegistry(private val parent: TranslationRegistry? = null) {
	private val selections = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()
	private val imports = ObjectArrayList<PendingImport>()
	private val declarations = Reference2ObjectOpenHashMap<Terminology, Object2ObjectOpenHashMap<EntryKey, Candidate>>()
	private val importRevisions = Reference2ObjectOpenHashMap<Candidate, SourceRevision>()
	private val derivations = Reference2ObjectOpenHashMap<Terminology, LocaleDerivationGraph>()

	@JvmRecord
	data class LanguageFile(val namespace: String, val locale: MinecraftLocale)

	private data class EntryKey(val key: TranslationKey, val locale: MinecraftLocale) {
		override fun toString(): String = "${key.namespace}/${key.value} (${locale.code()})"
	}

	private class Declaration(val localization: LocalizationDeclaration, val terminology: Terminology)

	private class PendingImport(val sourceLocale: MinecraftLocale, val translation: ImportedTranslation, val terminology: Terminology)

	private class Candidate(val text: String, val source: TranslationSource, val declaration: Declaration, val original: Selection? = null, val importPriority: Int = 0) {
		fun comparePriority(other: Candidate): Int {
			val sourcePriority = source.kind.priority.compareTo(other.source.kind.priority)
			return if (sourcePriority != 0) sourcePriority else importPriority.compareTo(other.importPriority)
		}
	}

	private class Resolved(
		val imported: Object2ObjectMap<EntryKey, Selection>,
		val glossary: Object2ObjectLinkedOpenHashMap<EntryKey, Selection>,
		val glossaryTerms: MutableMap<Declaration, GlossaryTerm>,
		val variants: Object2ObjectLinkedOpenHashMap<EntryKey, Selection>,
		val files: Map<LanguageFile, Map<String, String>>,
	)

	private class ExportEntry(val candidate: Candidate) {
		val sourceRevision = SourceRevision.of(candidate.declaration.localization.source).value
		val translations = ObjectArrayList<LocalizationDocument.Translation>()
	}

	private class Selection(private val key: EntryKey, var winner: Candidate) {
		private var ties: ObjectArrayList<Candidate>? = null
		private var signature: Int2IntMap? = null

		fun add(candidate: Candidate) {
			val precedence = candidate.comparePriority(winner)
			if (precedence > 0) {
				winner = candidate
				ties = null
				signature = null
			} else if (precedence == 0) {
				val candidates = ties ?: ObjectArrayList<Candidate>(1).also { ties = it }
				candidates.add(candidate)
			}
		}

		fun verifyText() {
			ties?.forEach { candidate ->
				require(candidate.text == winner.text) {
					"Conflicting translations for $key from ${winner.source.location} and ${candidate.source.location}"
				}
			}
		}

		fun forEachWinner(action: (Candidate) -> Unit) {
			action(winner)
			ties?.forEach(action)
		}

		fun verifyPlaceholders() {
			val expected = placeholderSignature()
			forEachWinner { candidate ->
				val original = candidate.original ?: return@forEachWinner
				require(original.placeholderSignature() == expected) { "Minecraft placeholder mismatch for $key at ${candidate.source.location}" }
			}
		}

		private fun placeholderSignature(): Int2IntMap {
			signature?.let { return it }
			return try {
				MinecraftTextFormat.signature(winner.text).also { signature = it }
			} catch (error: IllegalArgumentException) {
				throw IllegalArgumentException("Invalid Minecraft placeholder for $key at ${winner.source.location}", error)
			}
		}
	}

	@JvmOverloads
	fun add(key: TranslationKey, locale: MinecraftLocale, declaration: LocalizationDeclaration, source: TranslationSource, terminology: Terminology = Terminology.EMPTY) {
		val context = Declaration(declaration, terminology)
		val sourceKey = EntryKey(key, locale)
		val sourceCandidate = Candidate(declaration.source, source, context)
		val original = selections.add(sourceKey, sourceCandidate)
		val ownedDeclarations = declarations.computeIfAbsent(terminology) { Object2ObjectOpenHashMap() }
		val ownedDeclaration = ownedDeclarations[sourceKey]
		if (ownedDeclaration == null || sourceCandidate.comparePriority(ownedDeclaration) > 0) ownedDeclarations[sourceKey] = sourceCandidate
		for ((target, text) in declaration.translations) {
			selections.add(EntryKey(key, target), Candidate(text, source, context, original))
		}
		parent?.add(key, locale, declaration, source, terminology)
	}

	internal fun addImported(sourceLocale: MinecraftLocale, translation: ImportedTranslation, terminology: Terminology) {
		val key = EntryKey(translation.key, sourceLocale)
		require(declarations[terminology]?.containsKey(key) == true) {
			"No source declaration for import $key at ${translation.location}"
		}
		val target = key.copy(locale = translation.locale)
		require(target != key) { "Imported translation $target at ${translation.location} uses the source locale" }
		imports.add(PendingImport(sourceLocale, translation, terminology))
		parent?.addImported(sourceLocale, translation, terminology)
	}

	internal fun configureDerivations(terminology: Terminology, modId: String, rules: Collection<LocaleDerivation>) {
		derivations[terminology] = LocaleDerivationGraph(modId, rules)
		parent?.configureDerivations(terminology, modId, rules)
	}

	private fun Object2ObjectLinkedOpenHashMap<EntryKey, Selection>.add(key: EntryKey, candidate: Candidate): Selection {
		val selection = this[key]
		if (selection != null) {
			selection.add(candidate)
			return selection
		}
		return Selection(key, candidate).also { put(key, it) }
	}

	/** 解析各语言文件的条目。提供 [reportImport] 时，同时报告匹配到的外部译文及其当前状态。 */
	@JvmOverloads
	fun resolve(reportImport: Consumer<ImportedTranslation>? = null): Map<LanguageFile, Map<String, String>> = resolveAll(reportImport).files

	internal fun export(modId: String, sourceLocale: MinecraftLocale): LocalizationDocument {
		val resolved = resolveAll(reportWarnings = false)
		val entries = Object2ObjectLinkedOpenHashMap<TranslationKey, ExportEntry>()
		for (owned in declarations.values) {
			owned.object2ObjectEntrySet().fastForEach { (key, candidate) ->
				if (key.locale == sourceLocale) entries.computeIfAbsent(key.key) { ExportEntry(candidate) }
			}
		}

		forEachSelection(resolved.imported) { key, selection ->
			if (key.locale != sourceLocale) {
				selection.forEachWinner { candidate ->
					if (candidate.source.kind != Kind.IMPORTED) {
						val exported = entries.getValue(key.key)
						exported.translations.add(candidate.translation(key.locale, exported.sourceRevision))
					}
				}
			}
		}
		resolved.glossary.object2ObjectEntrySet().fastForEach { (key, selection) ->
			if (key.locale != sourceLocale) {
				selection.forEachWinner { candidate ->
					val exported = entries.getValue(key.key)
					exported.translations.add(candidate.translation(key.locale, exported.sourceRevision))
				}
			}
		}
		resolved.variants.object2ObjectEntrySet().fastForEach { (key, selection) ->
			if (key.locale != sourceLocale) {
				selection.forEachWinner { candidate ->
					val exported = entries.getValue(key.key)
					exported.translations.add(candidate.translation(key.locale, exported.sourceRevision))
				}
			}
		}
		for (pending in imports) {
			val key = EntryKey(pending.translation.key, pending.sourceLocale)
			val source = declarations[pending.terminology]!!.getValue(key)
			val revision = importRevisions.getValue(source)
			val translation = pending.translation.forRevision(revision)
			entries.getValue(translation.key).translations.add(
				LocalizationDocument.Translation(
					translation.locale,
					translation.text,
					TranslationSource(Kind.IMPORTED, translation.location),
					translation.sourceRevision.value,
					translation.status.name.lowercase(),
					translation.locked,
				),
			)
		}

		val result = ObjectArrayList<LocalizationDocument.Entry>(entries.size)
		entries.object2ObjectEntrySet().fastForEach { (key, exported) ->
			val declaration = exported.candidate.declaration
			val localization = declaration.localization
			val glossaryIds = ObjectOpenHashSet<String>(localization.glossary.size + localization.glossaryIds.size + 1)
			for (term in localization.glossary) glossaryIds.add(term.id)
			glossaryIds.addAll(localization.glossaryIds)
			val unitIds = ObjectOpenHashSet(localization.unitIds)
			resolved.glossaryTerms[declaration]?.let { term ->
				glossaryIds.add(term.id)
				unitIds.addAll(term.unitIds)
			}
			val domains = ObjectArrayList<String>(localization.domains.size)
			for (domain in localization.domains) domains.add(domain.id)
			domains.sort()
			val sortedGlossaryIds = ObjectArrayList(glossaryIds)
			sortedGlossaryIds.sort()
			val sortedUnitIds = ObjectArrayList(unitIds)
			sortedUnitIds.sort()
			exported.translations.sortWith(compareBy({ it.locale.code() }, { it.source.kind.name }, { it.source.location }, { it.text }))
			result.add(
				LocalizationDocument.Entry(
					key,
					localization.source,
					exported.sourceRevision,
					domains,
					localization.type?.id,
					sortedGlossaryIds,
					sortedUnitIds,
					MinecraftTextFormat.signature(localization.source),
					exported.translations,
				),
			)
		}
		result.sortWith(compareBy({ it.key.namespace }, { it.key.value }))
		return LocalizationDocument(modId, sourceLocale, result)
	}

	private fun Candidate.translation(locale: MinecraftLocale, sourceRevision: String): LocalizationDocument.Translation = LocalizationDocument.Translation(
		locale,
		text,
		source,
		sourceRevision,
		null,
		false,
	)

	private fun resolveAll(reportImport: Consumer<ImportedTranslation>? = null, reportWarnings: Boolean = true): Resolved {
		val imported = resolveImports(reportImport, reportWarnings)
		val files = Object2ObjectLinkedOpenHashMap<LanguageFile, MutableMap<String, String>>()
		forEachSelection(imported) { key, selection -> collectLanguageText(key, selection, files) }

		val glossaryTerms = Reference2ObjectOpenHashMap<Declaration, GlossaryTerm>()
		val glossary = collectGlossary(imported, glossaryTerms, reportWarnings)
		glossary.object2ObjectEntrySet().fastForEach { collectLanguageText(it.key, it.value, files) }
		val variants = collectDerivations(imported, glossary, glossaryTerms)
		variants.object2ObjectEntrySet().fastForEach { collectLanguageText(it.key, it.value, files) }
		return Resolved(imported, glossary, glossaryTerms, variants, files)
	}

	private fun resolveImports(reportImport: Consumer<ImportedTranslation>?, reportWarnings: Boolean): Object2ObjectMap<EntryKey, Selection> {
		if (imports.isEmpty) return Object2ObjectMaps.emptyMap()

		val selected = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()
		val logger = if (reportWarnings) datagen() else null
		for (pending in imports) {
			val key = EntryKey(pending.translation.key, pending.sourceLocale)
			val declarationCandidate = declarations[pending.terminology]!![key]!!
			val original = selections.getValue(key)
			val declaration = declarationCandidate.declaration
			val revision = importRevisions.getOrPut(declarationCandidate) { SourceRevision.of(declaration.localization.source) }
			val translation = pending.translation.forRevision(revision)
			reportImport?.accept(translation)

			val target = key.copy(locale = translation.locale)
			if (translation.status == STALE) {
				logger?.warn("Stale translation for {} at {} (reviewed revision {}, current revision {})", target, translation.location, translation.sourceRevision.value, revision.value)
				if (!translation.locked) continue
			}
			val existing = selections[target]
			if (existing != null && existing.winner.source.kind.priority > Kind.IMPORTED.priority) continue
			val source = TranslationSource(Kind.IMPORTED, translation.location)
			selected.add(target, Candidate(translation.text, source, declaration, original, translation.status.priority(translation.locked)))
		}
		return selected
	}

	private inline fun forEachSelection(imported: Object2ObjectMap<EntryKey, Selection>, action: (EntryKey, Selection) -> Unit) {
		val entries = selections.object2ObjectEntrySet().fastIterator()
		while (entries.hasNext()) {
			val entry = entries.next()
			action(entry.key, imported[entry.key] ?: entry.value)
		}
		val translations = Object2ObjectMaps.fastIterator(imported)
		while (translations.hasNext()) {
			val entry = translations.next()
			if (!selections.containsKey(entry.key)) action(entry.key, entry.value)
		}
	}

	private fun collectGlossary(
		imported: Object2ObjectMap<EntryKey, Selection>,
		terms: MutableMap<Declaration, GlossaryTerm>,
		reportWarnings: Boolean,
	): Object2ObjectLinkedOpenHashMap<EntryKey, Selection> {
		val glossary = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()
		forEachSelection(imported) { key, selection ->
			selection.forEachWinner { candidate ->
				if (candidate.original != null) return@forEachWinner
				val declaration = candidate.declaration
				val term = declaration.terminology.resolveTerm(key.key, declaration.localization, candidate.source) ?: return@forEachWinner
				terms[declaration] = term
				for (synonym in declaration.terminology.synonyms(term)) {
					val source = TranslationSource(Kind.GLOSSARY, "${candidate.source.location} (glossary ${synonym.id})")
					for ((locale, text) in synonym.preferredTranslations) {
						if (locale == key.locale) continue
						val target = key.copy(locale = locale)
						val explicit = imported[target] ?: selections[target]
						if (explicit == null) {
							glossary.add(target, Candidate(text, source, declaration, selection))
						} else {
							checkGlossaryText(target, synonym, text, explicit.winner, reportWarnings)
						}
					}
				}
			}
		}
		return glossary
	}

	private fun collectDerivations(
		imported: Object2ObjectMap<EntryKey, Selection>,
		glossary: Object2ObjectLinkedOpenHashMap<EntryKey, Selection>,
		terms: Map<Declaration, GlossaryTerm>,
	): Object2ObjectLinkedOpenHashMap<EntryKey, Selection> {
		val variants = Object2ObjectLinkedOpenHashMap<EntryKey, Selection>()

		fun expand(key: EntryKey, candidate: Candidate, original: Selection, graph: LocaleDerivationGraph, symbols: Collection<String>) {
			for (rule in graph.from(key.locale)) {
				val target = key.copy(locale = rule.output)
				if (selections.containsKey(target) || imported.containsKey(target) || glossary.containsKey(target)) continue
				val kind = if (candidate.source.kind == Kind.FALLBACK) Kind.FALLBACK else rule.kind
				val source = TranslationSource(kind, "${candidate.source.location} (${key.locale.code()} -> ${target.locale.code()})")
				val declaration = candidate.declaration
				val converted = rule.derive(candidate.text, symbols, key.key, declaration.localization)
				val variant = Candidate(converted, source, declaration, original)
				variants.add(target, variant)
				expand(target, variant, original, graph, symbols)
			}
		}

		fun expandFrom(key: EntryKey, selection: Selection) {
			selection.forEachWinner { candidate ->
				val declaration = candidate.declaration
				val graph = derivations[declaration.terminology] ?: LocaleDerivationGraph.DEFAULT
				if (graph.from(key.locale).isEmpty()) return@forEachWinner
				val symbols = unitSymbols(declaration, terms[declaration])
				expand(key, candidate, selection, graph, symbols)
			}
		}
		forEachSelection(imported, ::expandFrom)
		glossary.object2ObjectEntrySet().fastForEach { expandFrom(it.key, it.value) }
		return variants
	}

	private fun unitSymbols(declaration: Declaration, term: GlossaryTerm?): Collection<String> {
		val ids = declaration.localization.unitIds
		val termIds = term?.unitIds.orEmpty()
		if (ids.isEmpty() && termIds.isEmpty()) return emptyList()
		val unitSymbols = ObjectOpenHashSet<String>(ids.size + termIds.size)
		for (id in ids) unitSymbols.add(declaration.terminology.unitSymbol(id))
		for (id in termIds) unitSymbols.add(declaration.terminology.unitSymbol(id))
		return unitSymbols
	}

	private fun checkGlossaryText(key: EntryKey, term: GlossaryTerm, preferred: String, candidate: Candidate, reportWarnings: Boolean) {
		val text = candidate.text
		val forbidden = term.forbiddenTranslations[key.locale]?.any { it.equals(text, ignoreCase = true) } == true
		val accepted = preferred.equals(text, ignoreCase = true) || term.acceptedTranslations[key.locale]?.any { it.equals(text, ignoreCase = true) } == true
		if (reportWarnings && (forbidden || !accepted)) {
			datagen().warn("Glossary term {} for {} at {} prefers '{}', found '{}'", term.id, key, candidate.source.location, preferred, text)
		}
	}

	private fun collectLanguageText(key: EntryKey, selection: Selection, files: MutableMap<LanguageFile, MutableMap<String, String>>) {
		selection.verifyText()
		selection.verifyPlaceholders()
		files.computeIfAbsent(LanguageFile(key.key.namespace, key.locale)) { TreeMap() }[key.key.value] = selection.winner.text
	}
}
