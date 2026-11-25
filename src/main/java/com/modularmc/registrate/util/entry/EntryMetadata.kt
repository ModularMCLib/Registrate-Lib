package com.modularmc.registrate.util.entry

import com.modularmc.registrate.localization.EntryLocalization
import com.modularmc.registrate.providers.core.GeneratorType
import com.modularmc.registrate.util.nullness.NonNullConsumer

import java.util.Collections

/** 保存注册条目附带的数据生成回调和本地化声明。 */
class EntryMetadata(generators: Collection<DataGenerator<*>>, private val localization: EntryLocalization?) {
	private val generators = Collections.unmodifiableCollection(generators)

	/** 返回此条目提交时携带的数据生成回调。 */
	fun dataGenerators(): Collection<DataGenerator<*>> = generators

	/** 返回此条目的本地化声明。没有声明时返回 `null`。 */
	fun localization(): EntryLocalization? = localization

	/** 将一项生成回调关联到它接收的生成器类型。 */
	@JvmRecord
	data class DataGenerator<P : Any>(val type: GeneratorType<out P>, val callback: NonNullConsumer<out P>)
}
