package com.modularmc.registrate.localization.processor

import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.annotation.Trans
import com.modularmc.registrate.localization.annotation.TransVariants
import com.modularmc.registrate.localization.annotation.Translation
import com.modularmc.registrate.localization.index.LocalizationIndex
import com.modularmc.registrate.localization.index.LocalizationIndex.Entry

import java.io.DataOutputStream
import java.io.IOException
import java.util.EnumMap
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.StandardLocation

/** Collects the annotated declarations from all rounds of a downstream Java compilation. */
class TranslationProcessor : AbstractProcessor() {
	private val declarations = LinkedHashMap<String, MutableList<Declaration>>()

	private data class Declaration(val element: Element, val entry: Entry)

	override fun getSupportedAnnotationTypes(): Set<String> = setOf(Trans::class.java.name, TransVariants::class.java.name)

	override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

	override fun process(annotations: Set<TypeElement>, round: RoundEnvironment): Boolean {
		for (element in round.getElementsAnnotatedWith(Trans::class.java)) {
			val trans = element.getAnnotation(Trans::class.java)
			add(trans.modId, trans.namespace, trans.key, trans.source, trans.translations, element, "@Trans ${location(element)}")
		}
		for (element in round.getElementsAnnotatedWith(TransVariants::class.java)) {
			val group = element.getAnnotation(TransVariants::class.java)
			for (variant in group.value) {
				val key = LocalizationIndex.variantKey(group.keyPrefix, variant.id)
				add(group.modId, group.namespace, key, variant.source, variant.translations, element, "@TransVariants ${location(element)} [${variant.id}]")
			}
		}
		if (round.processingOver() && !round.errorRaised()) {
			declarations.forEach(::write)
		}
		return true
	}

	private fun add(modId: String, namespace: String, key: String, source: String, translations: Array<Translation>, element: Element, location: String) {
		val texts = if (translations.isEmpty()) {
			emptyMap()
		} else {
			EnumMap<MinecraftLocale, String>(MinecraftLocale::class.java).apply {
				for (translation in translations) put(translation.locale, translation.text)
			}
		}
		val entry = Entry(namespace.ifEmpty { modId }, key, source, location, texts)
		declarations.getOrPut(modId, ::ArrayList).add(Declaration(element, entry))
	}

	private fun write(modId: String, declarations: List<Declaration>) {
		val path = LocalizationIndex.path(modId)
		val origins = declarations.mapTo(LinkedHashSet(), Declaration::element).toTypedArray()
		try {
			val resource = processingEnv.filer.createResource(StandardLocation.CLASS_OUTPUT, "", path, *origins)
			DataOutputStream(resource.openOutputStream().buffered()).use { output ->
				LocalizationIndex.write(output, declarations.map(Declaration::entry))
			}
		} catch (error: IOException) {
			processingEnv.messager.printError("Cannot write localization index $path: ${error.message}")
		}
	}

	private fun location(element: Element): String {
		if (element is TypeElement) return element.qualifiedName.toString()
		val owner = element.enclosingElement as TypeElement
		return "${owner.qualifiedName}#${element.simpleName}"
	}
}
