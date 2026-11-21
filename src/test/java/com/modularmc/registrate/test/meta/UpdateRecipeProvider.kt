package com.modularmc.registrate.test.meta

import java.nio.file.Path

object UpdateRecipeProvider {
	@JvmStatic
	fun main(args: Array<String>) {
		MethodGenerator("RecipeProvider", UpdateRecipeProvider::class.java)
			.exclude("buildRecipes", "RecipeOutput")
			.generate(Path.of("src/main/java/com/modularmc/registrate/providers/generators/RegistrateRecipeProvider.kt"))
	}
}
