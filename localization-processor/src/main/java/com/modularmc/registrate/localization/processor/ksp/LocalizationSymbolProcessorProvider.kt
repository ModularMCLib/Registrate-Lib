package com.modularmc.registrate.localization.processor.ksp

import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.annotation.Trans
import com.modularmc.registrate.localization.annotation.TransVariants
import com.modularmc.registrate.localization.index.LocalizationIndex
import com.modularmc.registrate.localization.index.LocalizationIndex.Entry
import com.modularmc.registrate.localization.index.LocalizationIndex.Frontend

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Origin

import java.io.DataOutputStream
import java.util.EnumMap

class LocalizationSymbolProcessorProvider : SymbolProcessorProvider {
	override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = LocalizationSymbolProcessor(environment.codeGenerator)
}

/** Collects Kotlin declarations and writes one aggregating KSP resource per mod. */
private class LocalizationSymbolProcessor(private val codeGenerator: CodeGenerator) : SymbolProcessor {
	private class ModDeclarations {
		val entries = ArrayList<Entry>()
		val files = LinkedHashSet<KSFile>()
	}

	private val mods = LinkedHashMap<String, ModDeclarations>()

	override fun process(resolver: Resolver): List<KSAnnotated> {
		collect(resolver, Trans::class.java.name) { annotation, location, group, namespace ->
			group.entries.add(entry(namespace, annotation["key"] as String, annotation, "@Trans $location"))
		}
		collect(resolver, TransVariants::class.java.name) { annotation, location, group, namespace ->
			val prefix = annotation["keyPrefix"] as String
			for (variant in annotation["value"] as List<*>) {
				variant as KSAnnotation
				val id = variant["id"] as String
				group.entries.add(entry(namespace, LocalizationIndex.variantKey(prefix, id), variant, "@TransVariants $location [$id]"))
			}
		}
		return emptyList()
	}

	override fun finish() {
		for ((modId, group) in mods) {
			val dependencies = Dependencies(aggregating = true, *group.files.toTypedArray())
			val path = LocalizationIndex.path(modId, Frontend.KSP).removeSuffix(".bin")
			val resource = codeGenerator.createNewFileByPath(dependencies, path, "bin")
			DataOutputStream(resource.buffered()).use { LocalizationIndex.write(it, group.entries) }
		}
	}

	private inline fun collect(resolver: Resolver, annotationName: String, consume: (KSAnnotation, String, ModDeclarations, String) -> Unit) {
		for (symbol in resolver.getSymbolsWithAnnotation(annotationName)) {
			if (symbol.origin != Origin.KOTLIN) continue
			val declaration = symbol as KSDeclaration
			val annotation = declaration.annotations.first { it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName }
			val modId = annotation["modId"] as String
			val namespace = (annotation["namespace"] as String).ifEmpty { modId }
			val file = declaration.containingFile!!
			val group = mods.getOrPut(modId, ::ModDeclarations)
			group.files.add(file)
			consume(annotation, location(declaration, file), group, namespace)
		}
	}

	private fun entry(namespace: String, key: String, annotation: KSAnnotation, location: String): Entry {
		val translations = annotation["translations"] as List<*>
		val texts = if (translations.isEmpty()) {
			emptyMap()
		} else {
			EnumMap<MinecraftLocale, String>(MinecraftLocale::class.java).apply {
				for (value in translations) {
					value as KSAnnotation
					val locale = (value["locale"] as KSClassDeclaration).simpleName.asString()
					put(MinecraftLocale.valueOf(locale), value["text"] as String)
				}
			}
		}
		return Entry(namespace, key, annotation["source"] as String, location, texts)
	}

	private fun location(declaration: KSDeclaration, file: KSFile): String {
		val owner = declaration.parentDeclaration
		val name = if (declaration is KSPropertyDeclaration && owner != null) {
			"${owner.qualifiedName?.asString()}#${declaration.simpleName.asString()}"
		} else {
			declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
		}
		val line = (declaration.location as FileLocation).lineNumber
		return "$name (${file.fileName}:$line)"
	}

	private operator fun KSAnnotation.get(name: String): Any? = arguments.first { it.name?.asString() == name }.value
}
