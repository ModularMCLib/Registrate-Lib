package com.modularmc.registrate.localization.annotation

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.TranslationSource.Kind.DECLARED
import com.modularmc.registrate.localization.resolution.TranslationRegistry

import net.neoforged.fml.ModList
import net.neoforged.fml.loading.modscan.ModAnnotation.EnumHolder

import org.objectweb.asm.Type

import java.lang.annotation.ElementType
import java.util.EnumMap

/** Reads declarations from the owning mod file's NeoForge scan data during DataGen. */
internal object AnnotatedTranslations {
	private val trans = Type.getType(Trans::class.java)
	private val transVariants = Type.getType(TransVariants::class.java)

	fun collect(owner: AbstractRegistrate<*>, locale: MinecraftLocale, registry: TranslationRegistry) {
		val modId = owner.modid
		val defaults = owner.localizationDefaultValues()
		val terminology = owner.terminology()
		val annotations = ModList.get().getModFileById(modId).file.scanResult.annotations

		fun add(namespace: String, key: String, values: Map<*, *>, location: String) {
			val declaration = LocalizationDeclaration(
				values["source"] as String,
				translations(values),
				defaults.domains,
				defaults.type,
				defaults.glossary,
				defaults.glossaryIds,
				defaults.unitIds,
			)
			registry.add(TranslationKey(namespace, key), locale, declaration, TranslationSource(DECLARED, location), terminology)
		}

		for (annotation in annotations) {
			val type = annotation.annotationType()
			if (type != trans && type != transVariants) continue
			val values = annotation.annotationData()
			if (values["modId"] != modId) continue

			val namespace = (values["namespace"] as String?).orEmpty().ifEmpty { modId }
			val className = annotation.clazz().className
			val location = if (annotation.targetType() == ElementType.FIELD) "$className#${annotation.memberName()}" else className
			if (type == trans) {
				add(namespace, values["key"] as String, values, "@Trans $location")
			} else {
				val keyPrefix = values["keyPrefix"] as String
				val prefix = if (keyPrefix.endsWith('.')) keyPrefix else "$keyPrefix."
				for (variant in values["value"] as List<*>) {
					variant as Map<*, *>
					val id = variant["id"] as String
					add(namespace, "$prefix$id", variant, "@TransVariants $location [$id]")
				}
			}
		}
	}

	private fun translations(values: Map<*, *>): Map<MinecraftLocale, String> {
		val translations = values["translations"] as List<*>?
		if (translations.isNullOrEmpty()) return emptyMap()
		val texts = EnumMap<MinecraftLocale, String>(MinecraftLocale::class.java)
		for (translation in translations) {
			translation as Map<*, *>
			val locale = MinecraftLocale.valueOf((translation["locale"] as EnumHolder).value())
			texts[locale] = translation["text"] as String
		}
		return texts
	}
}
