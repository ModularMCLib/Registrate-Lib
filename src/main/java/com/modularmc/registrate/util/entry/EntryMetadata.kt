package com.modularmc.registrate.util.entry

import com.modularmc.registrate.localization.EntryLocalization
import com.modularmc.registrate.providers.core.GeneratorType
import com.modularmc.registrate.util.nullness.NonNullConsumer

import java.util.Collections

/** The data generation and localization declarations associated with a registration. */
class EntryMetadata(generators: Collection<DataGenerator<*>>, private val localization: EntryLocalization?) {
	private val generators = Collections.unmodifiableCollection(generators)

	fun dataGenerators(): Collection<DataGenerator<*>> = generators

	fun localization(): EntryLocalization? = localization

	@JvmRecord
	data class DataGenerator<P : Any>(val type: GeneratorType<out P>, val callback: NonNullConsumer<out P>)
}
