package com.modularmc.registrate.test.meta

import com.sun.source.tree.AnnotatedTypeTree
import com.sun.source.tree.ArrayTypeTree
import com.sun.source.tree.ClassTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.ParameterizedTypeTree
import com.sun.source.tree.PrimitiveTypeTree
import com.sun.source.tree.Tree
import com.sun.source.tree.WildcardTree
import com.sun.source.util.JavacTask

import java.net.URI
import javax.lang.model.element.Modifier
import javax.tools.Diagnostic
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider

/** Reads protected method signatures from a pasted Java class using the JDK parser. */
internal object ProtectedMethodScraper {
	data class Declarations(val imports: List<String>, val methods: List<MethodTree>)

	fun scrapeInput(owner: String): Declarations {
		println("Paste the $owner Java source, followed by 'done' or EOF:")
		val source = buildString {
			while (true) {
				val line = readlnOrNull() ?: break
				if (line == "done") break
				appendLine(line)
				if (line == "}") break
			}
		}
		val file = object : SimpleJavaFileObject(URI.create("string:///$owner.java"), JavaFileObject.Kind.SOURCE) {
			override fun getCharContent(ignoreEncodingErrors: Boolean): String = source
		}
		val task = ToolProvider.getSystemJavaCompiler().getTask(null, null, { diagnostic ->
			if (diagnostic.kind == Diagnostic.Kind.ERROR) error(diagnostic.toString())
		}, listOf("-proc:none"), null, listOf(file)) as JavacTask
		val sourceFile = task.parse().single()
		val imports = sourceFile.imports.filterNot { it.isStatic }.map { it.qualifiedIdentifier.toString() }
		val parent = sourceFile.typeDecls.filterIsInstance<ClassTree>().single { it.simpleName.contentEquals(owner) }
		val methods = parent.members.filterIsInstance<MethodTree>().filter {
			val flags = it.modifiers.flags
			it.returnType != null && Modifier.PROTECTED in flags && Modifier.FINAL !in flags && Modifier.ABSTRACT !in flags
		}
		return Declarations(imports, methods)
	}

	private val typeNames = mapOf(
		"void" to "Unit",
		"boolean" to "Boolean",
		"byte" to "Byte",
		"char" to "Char",
		"short" to "Short",
		"int" to "Int",
		"long" to "Long",
		"float" to "Float",
		"double" to "Double",
		"Object" to "Any",
		"Integer" to "Int",
		"Character" to "Char",
		"List" to "MutableList",
		"Set" to "MutableSet",
		"Map" to "MutableMap",
		"Collection" to "MutableCollection",
	)

	fun kotlinType(type: Tree): String = when (type) {
		is ParameterizedTypeTree -> type.typeArguments.joinToString(", ", "${kotlinType(type.type)}<", ">") { kotlinType(it) }

		is ArrayTypeTree -> {
			val element = kotlinType(type.type)
			if (type.type is PrimitiveTypeTree) "${element}Array" else "Array<$element>"
		}

		is WildcardTree -> when (type.kind) {
			Tree.Kind.EXTENDS_WILDCARD -> "out ${kotlinType(type.bound)}"
			Tree.Kind.SUPER_WILDCARD -> "in ${kotlinType(type.bound)}"
			else -> "*"
		}

		is AnnotatedTypeTree -> kotlinType(type.underlyingType) + if (type.annotations.any { isNullable(it.annotationType) }) "?" else ""

		else -> type.toString().let { typeNames[it] ?: it }
	}

	fun isNullable(annotationType: Tree): Boolean = annotationType.toString().substringAfterLast('.') == "Nullable"
}
