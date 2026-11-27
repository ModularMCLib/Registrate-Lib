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
import net.neoforged.neoforgespi.language.ModFileScanData.AnnotationData

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

import java.lang.annotation.ElementType
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumMap

/** 数据生成时从所属模组文件的 NeoForge 扫描结果中读取本地化声明。 */
internal object AnnotatedTranslations {
	private val trans = Type.getType(Trans::class.java)
	private val transVariants = Type.getType(TransVariants::class.java)

	fun collect(owner: AbstractRegistrate<*>, locale: MinecraftLocale, registry: TranslationRegistry) {
		val modId = owner.modid
		val defaults = owner.localizationDefaultValues()
		val terminology = owner.terminology()
		val modFile = ModList.get().getModFileById(modId)
		val singleModFile = modFile.mods.size == 1

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

		val annotations = modFile.file.scanResult.annotations
		val classGroups = annotations.asSequence()
			.filter { it.annotationType() == transVariants && it.targetType() == ElementType.TYPE }
			.associate { it.clazz() to it.annotationData() }
		val fieldConstants = HashMap<Type, Map<String, String>>()
		for (annotation in annotations) {
			val type = annotation.annotationType()
			if (type != trans && type != transVariants) continue
			val values = annotation.annotationData()
			val explicitKey = values["key"] as String?
			val namePrefix = (values["namePrefix"] as String?).orEmpty()
			val valuePrefix = (values["valuePrefix"] as String?).orEmpty()
			val useClassGroup = type == trans && annotation.targetType() == ElementType.FIELD &&
				explicitKey == null && namePrefix.isEmpty() && valuePrefix.isEmpty()
			val group = if (useClassGroup) classGroups[annotation.clazz()] else null
			val declaredModId = (values["modId"] as String?).orEmpty().ifEmpty { (group?.get("modId") as String?).orEmpty() }
			if (declaredModId.isEmpty()) {
				check(singleModFile) { "${annotation.location()} must declare modId because its file contains multiple mods" }
			} else if (declaredModId != modId) {
				continue
			}

			val namespace = (values["namespace"] as String?).orEmpty()
				.ifEmpty { (group?.get("namespace") as String?).orEmpty() }
				.ifEmpty { modId }
			val location = annotation.location()
			if (type == trans) {
				val key = explicitKey ?: run {
					check(annotation.targetType() == ElementType.FIELD) { "@Trans $location must declare key" }
					val memberName = annotation.memberName()
					val groupPrefix = group?.get("keyPrefix") as String?
					when {
						namePrefix.isNotEmpty() -> prefixed(namePrefix, memberName)

						groupPrefix != null && group["useFieldValues"] != true -> prefixed(groupPrefix, memberName)

						else -> {
							val constants = fieldConstants.getOrPut(annotation.clazz()) {
								readStringConstants(modFile.file.findResource("${annotation.clazz().internalName}.class"))
							}
							val value = constants[memberName]
							check(!value.isNullOrEmpty()) { "@Trans $location requires a non-empty compile-time String constant to derive its key" }
							val prefix = groupPrefix ?: valuePrefix
							if (prefix.isEmpty()) value else prefixed(prefix, value)
						}
					}
				}
				add(namespace, key, values, "@Trans $location")
			} else {
				val keyPrefix = values["keyPrefix"] as String
				for (variant in (values["value"] as List<*>?).orEmpty()) {
					variant as Map<*, *>
					val id = variant["id"] as String
					add(namespace, prefixed(keyPrefix, id), variant, "@TransVariants $location [$id]")
				}
			}
		}
	}

	private fun prefixed(prefix: String, suffix: String): String = if (prefix.endsWith('.')) "$prefix$suffix" else "$prefix.$suffix"

	// NeoForge 的注解扫描结果没有记录字段常量，直接从所属模组的字节码读取。
	private fun readStringConstants(path: Path): Map<String, String> {
		val constants = HashMap<String, String>()
		val reader = Files.newInputStream(path).use { ClassReader(it) }
		reader.accept(
			object : ClassVisitor(Opcodes.ASM9) {
				override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
					val required = Opcodes.ACC_STATIC or Opcodes.ACC_FINAL
					if (access and required == required && value is String) constants[name] = value
					return null
				}
			},
			ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
		)
		return constants
	}

	private fun AnnotationData.location(): String {
		val className = clazz().className
		return if (targetType() == ElementType.FIELD) "$className#${memberName()}" else className
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
