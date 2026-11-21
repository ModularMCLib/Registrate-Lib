package com.modularmc.registrate.providers.core

import com.modularmc.registrate.localization.resolution.TranslationRegistry

import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.data.PackOutput.Target
import net.minecraft.resources.Identifier

import com.google.gson.JsonObject

import java.util.concurrent.CompletableFuture

internal object LanguageOutput {
	@JvmStatic
	internal fun write(cache: CachedOutput, output: PackOutput, registry: TranslationRegistry): CompletableFuture<*> {
		val paths = output.createPathProvider(Target.RESOURCE_PACK, "lang")
		val files = registry.resolve()
		val entries = files.entries.iterator()
		val writes = Array(files.size) {
			val (file, texts) = entries.next()
			val path = paths.json(Identifier.fromNamespaceAndPath(file.namespace, file.locale.code()))
			val language = JsonObject()
			texts.forEach(language::addProperty)
			DataProvider.saveStable(cache, language, path)
		}
		return CompletableFuture.allOf(*writes)
	}
}
