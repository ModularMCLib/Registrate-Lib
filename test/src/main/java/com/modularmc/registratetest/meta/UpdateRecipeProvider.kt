package com.modularmc.registratetest.meta

import java.nio.file.Path

/** 从原版配方提供器源码更新可公开调用的转发方法。 */
object UpdateRecipeProvider {
	/** 读取原版方法列表，更新配方生成器，并跳过由库维护生成流程的入口。 */
	@JvmStatic
	fun main(args: Array<String>) {
		MethodGenerator("RecipeProvider", UpdateRecipeProvider::class.java)
			.exclude("buildRecipes", "RecipeOutput")
			.generate(Path.of("src/main/java/com/modularmc/registrate/providers/generators/RegistrateRecipeProvider.kt"))
	}
}
