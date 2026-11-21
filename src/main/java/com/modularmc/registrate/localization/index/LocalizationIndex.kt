package com.modularmc.registrate.localization.index

import com.modularmc.registrate.localization.MinecraftLocale

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.EnumMap
import java.util.function.Consumer

/** The declaration protocol used by compiler frontends and the DataGen reader. */
object LocalizationIndex {
	private const val MAGIC = -0x524c494e
	private const val VERSION = 1

	private val entryOrder = compareBy(Entry::namespace, Entry::key, Entry::location)

	enum class Frontend(val suffix: String) {
		JAVAC(""),
		KSP(".ksp"),
	}

	@JvmRecord
	data class Entry(val namespace: String, val key: String, val source: String, val location: String, val translations: Map<MinecraftLocale, String>)

	@JvmStatic
	@JvmOverloads
	fun path(modId: String, frontend: Frontend = Frontend.JAVAC): String = "META-INF/registratelib/localization/$modId${frontend.suffix}.bin"

	@JvmStatic
	fun variantKey(prefix: String, id: String): String = if (prefix.endsWith('.')) "$prefix$id" else "$prefix.$id"

	@JvmStatic
	@Throws(IOException::class)
	fun write(output: DataOutputStream, entries: Collection<Entry>) {
		output.writeInt(MAGIC)
		output.writeInt(VERSION)
		output.writeInt(entries.size)
		for ((namespace, key, source, location, translations) in entries.sortedWith(entryOrder)) {
			output.writeUTF(namespace)
			output.writeUTF(key)
			output.writeUTF(source)
			output.writeUTF(location)
			output.writeInt(translations.size)
			for ((locale, text) in translations) {
				output.writeUTF(locale.name)
				output.writeUTF(text)
			}
		}
	}

	@JvmStatic
	@Throws(IOException::class)
	fun read(input: DataInputStream, consumer: Consumer<Entry>) {
		val leading = input.readInt()
		val count = if (leading == MAGIC) {
			val version = input.readInt()
			if (version != VERSION) {
				throw IOException("Unsupported localization index version $version")
			}
			input.readInt()
		} else {
			leading // Version 0 began with the entry count.
		}

		if (count < 0) {
			throw IOException("Invalid localization index entry count $count")
		}
		repeat(count) {
			val namespace = input.readUTF()
			val key = input.readUTF()
			val source = input.readUTF()
			val location = input.readUTF()
			val targetCount = input.readInt()
			if (targetCount < 0) {
				throw IOException("Invalid translation count $targetCount for $namespace/$key")
			}
			val translations = if (targetCount == 0) {
				emptyMap()
			} else {
				EnumMap<MinecraftLocale, String>(MinecraftLocale::class.java).apply {
					repeat(targetCount) {
						put(MinecraftLocale.valueOf(input.readUTF()), input.readUTF())
					}
				}
			}
			consumer.accept(Entry(namespace, key, source, location, translations))
		}
	}
}
