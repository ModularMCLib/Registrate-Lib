package com.modularmc.registrate.internal.lifecycle

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.internal.event.OneTimeEventReceiver

import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.registries.RegisterEvent

import java.util.function.Consumer

/** 把单个 Registrate 实例的注册和数据生成回调接入模组事件总线。 */
class RegistrateLifecycle(private val owner: AbstractRegistrate<*>, private val hooks: Hooks) {
	/** 定义注册、创造模式物品栏和数据生成回调。NeoForge 派发事件前，所属 Registrate 会把这些回调接入模组事件总线。 */
	interface Hooks {
		/** 返回绑定的模组事件总线，尚未绑定时返回 `null`。 */
		fun modEventBus(): IEventBus?

		/** 保存注册期间使用的模组事件总线。 */
		fun setModEventBus(bus: IEventBus)

		/** 返回当前启动是否正在运行数据生成，用于决定是否订阅收集数据事件。 */
		fun doDatagen(): Boolean

		/** 在普通优先级接收注册事件，处理当前注册表的条目。 */
		fun onRegister(event: RegisterEvent)

		/** 在最低优先级接收注册事件，执行等待当前注册表写入结束的操作。 */
		fun onRegisterLate(event: RegisterEvent)

		/** 接收创造模式标签页填充事件，为对应的标签页追加内容。 */
		fun onBuildCreativeModeTabContents(event: BuildCreativeModeTabContentsEvent)

		/** 接收数据收集事件，为本轮生成登记所需的提供器。 */
		fun onData(event: GatherDataEvent)
	}

	/** 在模组初始化期间挂接注册、创造模式标签页和数据生成所需的监听器。注册监听器会在公共初始化阶段排队移除。 */
	fun register(bus: IEventBus) {
		if (hooks.modEventBus() == null) {
			hooks.setModEventBus(bus)
		}

		val onRegister = Consumer(hooks::onRegister)
		val onRegisterLate = Consumer(hooks::onRegisterLate)
		bus.addListener(EventPriority.NORMAL, RegisterEvent::class.java, onRegister)
		bus.addListener(EventPriority.LOWEST, RegisterEvent::class.java, onRegisterLate)
		bus.addListener(BuildCreativeModeTabContentsEvent::class.java, hooks::onBuildCreativeModeTabContents)

		OneTimeEventReceiver.addModListener(owner, FMLCommonSetupEvent::class.java) {
			OneTimeEventReceiver.unregister(owner, onRegister, RegisterEvent::class.java)
			OneTimeEventReceiver.unregister(owner, onRegisterLate, RegisterEvent::class.java)
		}

		if (hooks.doDatagen()) {
			OneTimeEventReceiver.addModListener(owner, GatherDataEvent.Client::class.java, hooks::onData)
			OneTimeEventReceiver.addModListener(owner, GatherDataEvent.Server::class.java, hooks::onData)
		}
	}
}
