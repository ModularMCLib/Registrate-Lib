package com.modularmc.registratetest.meta

import java.nio.file.Path

object UpdateBlockLootTables {
	@JvmStatic
	fun main(args: Array<String>) {
		MethodGenerator("BlockLootSubProvider", UpdateBlockLootTables::class.java)
			.exclude("generate")
			.exclude("getKnownBlocks")
			.generate(Path.of("src/main/java/com/modularmc/registrate/providers/loot/RegistrateBlockLootTables.kt"))
	}
}
