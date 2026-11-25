package com.modularmc.registratetest.gametests

import com.modularmc.registratetest.mod.TestMod

import net.neoforged.testframework.DynamicTest
import net.neoforged.testframework.annotation.ForEachTest
import net.neoforged.testframework.annotation.TestHolder
import net.neoforged.testframework.gametest.EmptyTemplate
import net.neoforged.testframework.gametest.GameTest

/** 覆盖自定义物品的注册和默认堆叠数据。 */
@ForEachTest(groups = ["custom-item"])
object CustomItemTests {
	/** 在游戏测试中读取示例模组的自定义物品，检查实际堆叠的数据组件。 */
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
