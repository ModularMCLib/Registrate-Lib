package com.modularmc.registratetest.meta

import java.nio.file.Path

/** 从原版方块掉落子提供器源码更新可公开调用的转发方法。 */
object UpdateBlockLootTables {
	/** 读取原版方法列表，并替换库中预留的方块掉落转发区。 */
	@JvmStatic
	fun main(args: Array<String>) {
		MethodGenerator("BlockLootSubProvider", UpdateBlockLootTables::class.java)
			.exclude("generate")
			.exclude("getKnownBlocks")
			.generate(Path.of("src/main/java/com/modularmc/registrate/providers/loot/RegistrateBlockLootTables.kt"))
	}
}
