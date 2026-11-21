package com.modularmc.registratetest.gametests

import com.modularmc.registratetest.mod.TestMod

import net.neoforged.testframework.DynamicTest
import net.neoforged.testframework.annotation.ForEachTest
import net.neoforged.testframework.annotation.TestHolder
import net.neoforged.testframework.gametest.EmptyTemplate
import net.neoforged.testframework.gametest.GameTest

@ForEachTest(groups = ["custom-item"])
object CustomItemTests {
	@JvmStatic
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(description = ["Ensure custom item is registered and has the expected properties"])
	fun ensureItemContent(test: DynamicTest) {
		test.onGameTest { helper ->
			val stack = TestMod.instance().testitem.asStack()
			helper.assertValueEqual("item.testmod.testitem", stack.item.descriptionId, "Test Item translation key")
			helper.succeed()
		}
	}
}
