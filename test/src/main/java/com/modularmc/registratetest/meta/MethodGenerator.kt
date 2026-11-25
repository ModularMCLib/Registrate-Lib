package com.modularmc.registratetest.meta

import com.modularmc.registratetest.meta.ProtectedMethodScraper.isNullable
import com.modularmc.registratetest.meta.ProtectedMethodScraper.kotlinType

import com.sun.source.tree.AnnotatedTypeTree
import com.sun.source.tree.ArrayTypeTree
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MethodTree
import com.sun.source.util.TreeScanner

import java.nio.file.Files
import java.nio.file.Path
import javax.lang.model.element.Modifier

/** 根据原版方法签名重新生成 Kotlin 数据提供器中的公共重写和伴生对象方法。 */
internal class MethodGenerator(private val owner: String, private val updater: Class<*>) {
	private data class Exclusion(val name: String, val parameterTypes: List<String>?)

	private enum class Section {
		OVERRIDE,
		STATIC,
	}

	private val exclusions = HashSet<Exclusion>()

	fun exclude(name: String, vararg parameterTypes: String) = apply {
		exclusions.add(Exclusion(name, parameterTypes.asList().ifEmpty { null }))
	}

	fun generate(output: Path) {
		val source = Files.readAllLines(output)
		val declarations = ProtectedMethodScraper.scrapeInput(owner)
		val methods = declarations.methods.filterNot { method ->
			val methodName = method.name.toString()
			Exclusion(methodName, null) in exclusions ||
				Exclusion(methodName, method.parameters.map { it.type.toString() }) in exclusions
		}
		val (staticMethods, overrides) = methods.partition { Modifier.STATIC in it.modifiers.flags }
		replaceMethods(source, Section.OVERRIDE, overrides)
		replaceMethods(source, Section.STATIC, staticMethods)
		addImports(source, declarations.imports, methods)
		Files.write(output, source)
		println("Updated $output with ${methods.size} methods")
	}

	private fun addImports(source: MutableList<String>, imports: List<String>, methods: List<MethodTree>) {
		val types = HashSet<String>()
		val scanTypes = object : TreeScanner<Unit, Unit>() {
			override fun visitIdentifier(node: IdentifierTree, data: Unit) {
				types.add(kotlinType(node))
			}

			override fun visitAnnotatedType(node: AnnotatedTypeTree, data: Unit) {
				scan(node.underlyingType, data)
			}
		}
		for (method in methods) {
			scanTypes.scan(method.returnType, Unit)
			for (parameter in method.parameters) scanTypes.scan(parameter.type, Unit)
			for (parameter in method.typeParameters) scanTypes.scan(parameter.bounds, Unit)
		}
		val existing = source.filterTo(HashSet()) { it.startsWith("import ") }
		val needed = imports.filter { it.substringAfterLast('.') in types }.map { "import $it" }.filterNot(existing::contains)
		source.addAll(source.indexOfLast { it.startsWith("import ") } + 1, needed)
	}

	private fun replaceMethods(source: MutableList<String>, region: Section, methods: List<MethodTree>) {
		val start = source.indexOfFirst { it.trim() == "// GENERATED $region START" }
		val end = source.indexOfFirst { it.trim() == "// GENERATED $region END" }
		val indent = source[start].takeWhile(Char::isWhitespace)
		val lines = source.subList(start + 1, end)
		lines.clear()
		for (method in methods) {
			lines.add("")
			lines.addAll(stub(method, region).lineSequence().map { indent + it })
		}
		lines.add("")
	}

	private fun stub(method: MethodTree, section: Section): String = buildString {
		val isStatic = section == Section.STATIC
		val receiver = if (isStatic) owner else "super"
		val name = method.name.toString()
		appendLine("/** Exposes [$owner.$name]. */")
		appendLine("@Generated(value = [\"${updater.name}\"])")
		if (isStatic) appendLine("@JvmStatic")
		append(if (isStatic) "fun " else "public override fun ")
		val typeParameters = method.typeParameters
		if (typeParameters.isNotEmpty()) {
			append(typeParameters.joinToString(", ", "<", "> ") { it.name.toString() })
		}
		append(name)
		val arguments = ArrayList<String>(method.parameters.size)
		append(
			method.parameters.joinToString(", ", "(", ")") { parameter ->
				val paramName = parameter.name.toString()
				val vararg = parameter.toString().substringBeforeLast(' ').endsWith("...")
				arguments.add(if (vararg) "*$paramName" else paramName)
				val type = if (vararg) (parameter.type as ArrayTypeTree).type else parameter.type
				val renderedType = kotlinType(type)
				val nullable = parameter.modifiers.annotations.any { isNullable(it.annotationType) }
				val prefix = if (vararg) "vararg " else ""
				val suffix = if (nullable && !renderedType.endsWith('?')) "?" else ""
				"$prefix$paramName: $renderedType$suffix"
			},
		)
		val returnType = kotlinType(method.returnType)
		if (returnType != "Unit") {
			val nullable = method.modifiers.annotations.any { isNullable(it.annotationType) }
			append(": ").append(returnType)
			if (nullable && !returnType.endsWith("?")) append('?')
		}
		val bounds = typeParameters.flatMap { parameter ->
			parameter.bounds.map { "${parameter.name} : ${kotlinType(it)}" }
		}
		if (bounds.isNotEmpty()) append(bounds.joinToString(", ", " where "))
		append(" = ").append(receiver).append('.').append(name)
		if (typeParameters.isNotEmpty()) append(typeParameters.joinToString(", ", "<", ">") { it.name.toString() })
		append(arguments.joinToString(", ", "(", ")"))
	}
}
