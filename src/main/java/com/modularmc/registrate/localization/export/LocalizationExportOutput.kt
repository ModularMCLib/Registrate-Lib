package com.modularmc.registrate.localization.export

import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TranslationSource

import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.data.PackOutput.Target
import net.minecraft.resources.Identifier

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.ints.Int2IntMaps

import java.util.Locale
import java.util.concurrent.CompletableFuture

internal object LocalizationExportOutput {
	fun write(cache: CachedOutput, output: PackOutput, document: LocalizationDocument): CompletableFuture<*> {
		val paths = output.createPathProvider(Target.DATA_PACK, "registrate")
		val path = paths.json(Identifier.fromNamespaceAndPath(document.modId, "localization"))
		return DataProvider.saveStable(cache, toJson(document), path)
	}

	private fun toJson(document: LocalizationDocument): JsonObject {
		val json = JsonObject()
		json.addProperty("formatVersion", 1)
		json.addProperty("modId", document.modId)
		json.addProperty("sourceLocale", document.sourceLocale.code())
		val entries = JsonArray()
		for (entry in document.entries) entries.add(entry(entry, document.sourceLocale))
		json.add("entries", entries)
		return json
	}

	private fun entry(value: LocalizationDocument.Entry, sourceLocale: MinecraftLocale): JsonObject {
		val json = JsonObject()
		json.addProperty("namespace", value.key.namespace)
		json.addProperty("key", value.key.value)
		val source = JsonObject()
		source.addProperty("locale", sourceLocale.code())
		source.addProperty("text", value.sourceText)
		source.addProperty("revision", value.sourceRevision)
		json.add("source", source)
		val context = JsonObject()
		context.add("domains", strings(value.domains))
		if (value.type == null) context.add("type", JsonNull.INSTANCE) else context.addProperty("type", value.type)
		context.add("glossaryIds", strings(value.glossaryIds))
		context.add("unitIds", strings(value.unitIds))
		val placeholders = JsonObject()
		val indexes = IntArray(value.placeholderSignature.size)
		val iterator = Int2IntMaps.fastIterator(value.placeholderSignature)
		var index = 0
		while (iterator.hasNext()) indexes[index++] = iterator.next().intKey
		indexes.sort()
		for (argument in indexes) placeholders.addProperty(argument.toString(), value.placeholderSignature.get(argument))
		context.add("placeholderSignature", placeholders)
		json.add("context", context)
		val translations = JsonArray()
		for (translation in value.translations) translations.add(translation(translation))
		json.add("translations", translations)
		return json
	}

	private fun translation(value: LocalizationDocument.Translation): JsonObject {
		val json = JsonObject()
		json.addProperty("locale", value.locale.code())
		json.addProperty("text", value.text)
		val source = JsonObject()
		source.addProperty("kind", sourceKind(value.source.kind))
		source.addProperty("location", value.source.location)
		json.add("source", source)
		json.addProperty("sourceRevision", value.sourceRevision)
		if (value.status == null) json.add("status", JsonNull.INSTANCE) else json.addProperty("status", value.status)
		json.addProperty("locked", value.locked)
		return json
	}

	private fun strings(values: Iterable<String>): JsonArray {
		val array = JsonArray()
		for (value in values) array.add(value)
		return array
	}

	private fun sourceKind(kind: TranslationSource.Kind): String = kind.name.lowercase(Locale.ROOT)
}
