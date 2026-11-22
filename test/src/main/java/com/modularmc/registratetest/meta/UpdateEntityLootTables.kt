package com.modularmc.registratetest.meta

import java.nio.file.Path

object UpdateEntityLootTables {
	@JvmStatic
	fun main(args: Array<String>) {
		MethodGenerator("EntityLootSubProvider", UpdateEntityLootTables::class.java)
			.exclude("generate")
			.exclude("isNonLiving")
			.exclude("getKnownEntityTypes")
			.generate(Path.of("src/main/java/com/modularmc/registrate/providers/loot/RegistrateEntityLootTables.kt"))
	}
}
