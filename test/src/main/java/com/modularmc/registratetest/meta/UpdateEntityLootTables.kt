package com.modularmc.registratetest.meta

import java.nio.file.Path

/** 从原版实体掉落子提供器源码更新可公开调用的转发方法。 */
object UpdateEntityLootTables {
	/** 读取原版方法列表，并替换库中预留的实体掉落转发区。 */
	@JvmStatic
	fun main(args: Array<String>) {
		MethodGenerator("EntityLootSubProvider", UpdateEntityLootTables::class.java)
			.exclude("generate")
			.exclude("isNonLiving")
			.exclude("getKnownEntityTypes")
			.generate(Path.of("src/main/java/com/modularmc/registrate/providers/loot/RegistrateEntityLootTables.kt"))
	}
}
