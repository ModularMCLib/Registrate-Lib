package com.modularmc.registrate.localization

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

/** 标识译文审校时对应的源文版本，用于判断源文是否已变更。 */
@JvmRecord
data class SourceRevision(val value: String) {
	companion object {
		private val digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }
		private val hex = HexFormat.of()

		/** 按 UTF-8 编码计算源文的 SHA-256 摘要。 */
		@JvmStatic
		fun of(source: String): SourceRevision = SourceRevision(hex.formatHex(digest.get().digest(source.toByteArray(StandardCharsets.UTF_8))))
	}
}

/** 记录外部译文的审校状态，供数据生成时比较导入译文的优先级。 */
enum class TranslationStatus(private val priority: Int) {
	STALE(0),
	DRAFT(1),
	IN_REVIEW(2),
	APPROVED(3),
	;

	internal fun priority(locked: Boolean): Int = priority + if (locked) entries.size else 0
}

/** 从外部导入的译文。[location] 记录来源，供数据生成时定位问题。 */
@JvmRecord
data class ImportedTranslation(
	val key: TranslationKey,
	val locale: MinecraftLocale,
	val text: String,
	val sourceRevision: SourceRevision,
	val status: TranslationStatus,
	val locked: Boolean,
	val location: String,
) {
	internal fun forRevision(revision: SourceRevision): ImportedTranslation = if (sourceRevision == revision || status == TranslationStatus.STALE) this else copy(status = TranslationStatus.STALE)
}
