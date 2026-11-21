package com.modularmc.registrate.localization.terminology

import com.modularmc.registrate.localization.terminology.LocalizationCatalog.GlossaryTerm

/**
 * Supplies terminology to a mod's [LocalizationCatalog] from any storage format.
 * Register a glossary during mod setup and keep its lookups stable throughout DataGen.
 * Term IDs identify a single definition across the mod's glossaries.
 */
interface Glossary {
	/** Looks up a term by its stable ID. Returns null when the glossary has no such term. */
	fun find(id: String): GlossaryTerm?

	/**
	 * Finds all senses of a complete source label or alias, including those with different domains or text types.
	 * The query uses locale-independent lowercase. An unmatched label returns an empty iterable.
	 */
	fun findBySource(normalizedSource: String): Iterable<GlossaryTerm>
}
