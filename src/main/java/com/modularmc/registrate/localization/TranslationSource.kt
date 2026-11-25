package com.modularmc.registrate.localization

/** 记录译文的来源声明及其优先级，供数据生成时选择译文。 */
@JvmRecord
data class TranslationSource(val kind: Kind, val location: String) {
	/** 区分译文来源。数据生成时将来源优先级用于比较同一个键和语言的候选文本。 */
	enum class Kind(internal val priority: Int) {
		FALLBACK(0),
		VARIANT(1),
		AUTOMATIC(2),
		GLOSSARY(3),
		IMPORTED(4),
		DECLARED(5),
	}
}
