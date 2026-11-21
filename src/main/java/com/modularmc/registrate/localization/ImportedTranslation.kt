package com.modularmc.registrate.localization

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

/** Identifies the source text against which a translation was reviewed. */
@JvmRecord
data class SourceRevision(val value: String) {
	companion object {
		private val digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }
		private val hex = HexFormat.of()

		/** Computes the SHA-256 of the UTF-8 source text. */
		@JvmStatic
		fun of(source: String): SourceRevision = SourceRevision(hex.formatHex(digest.get().digest(source.toByteArray(StandardCharsets.UTF_8))))
	}
}

/** Review state attached to a translation imported from another source. */
enum class TranslationStatus(private val priority: Int) {
	STALE(0),
	DRAFT(1),
	IN_REVIEW(2),
	APPROVED(3),
	;

	internal fun priority(locked: Boolean): Int = priority + if (locked) entries.size else 0
}

/** A translation received from another source. [location] identifies its origin in DataGen diagnostics. */
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
