package com.modularmc.registrate.internal.event

import com.modularmc.registrate.AbstractRegistrate

import net.neoforged.bus.api.Event
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.IModBusEvent
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet

import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/** 将模组事件监听器限制为一次执行，并将注销安排到允许修改监听器的阶段。 */
class OneTimeEventReceiver<T : Event>(private val bus: IEventBus, private val listener: Consumer<in T>) : Consumer<T> {
	private val consumed = AtomicBoolean()

	/** 首次收到事件时调用监听器，成功返回后将自身加入注销队列。 */
	override fun accept(event: T) {
		if (consumed.compareAndSet(false, true)) {
			listener.accept(event)
			unregister(bus, this)
		}
	}

	companion object {
		private val initializedOwners = ReferenceOpenHashSet<AbstractRegistrate<*>>()
		private val waitingModListeners = Reference2ObjectOpenHashMap<AbstractRegistrate<*>, MutableList<Consumer<IEventBus>>>()
		private val toUnregister = ArrayList<Runnable>()

		/** 以普通优先级订阅单次模组事件。实例尚未绑定事件总线时暂存监听器。 */
		@JvmStatic
		fun <T> addModListener(owner: AbstractRegistrate<*>, eventClass: Class<T>, listener: Consumer<in T>) where T : Event, T : IModBusEvent {
			addModListener(owner, EventPriority.NORMAL, eventClass, listener)
		}

		/**
		 * 以指定优先级订阅单次模组事件。已暂存的监听器在首次使用所属实例的总线时一起挂接。
		 * @param owner 事件监听器所属的 Registrate 实例
		 * @param priority 监听器执行优先级
		 * @param eventClass 接收的模组事件类型
		 * @param listener 首次收到事件时执行的回调
		 */
		@JvmStatic
		fun <T> addModListener(owner: AbstractRegistrate<*>, priority: EventPriority, eventClass: Class<T>, listener: Consumer<in T>) where T : Event, T : IModBusEvent {
			val bus = owner.modEventBus
			if (bus == null) {
				waitingModListeners.getOrPut(owner, ::ArrayList).add { addListener(it, priority, eventClass, listener) }
				return
			}

			if (initializedOwners.add(owner)) {
				waitingModListeners.remove(owner)?.forEach { it.accept(bus) }
				addModListener(owner, FMLLoadCompleteEvent::class.java, ::onLoadComplete)
			}
			addListener(bus, priority, eventClass, listener)
		}

		/** 在模组加载完成时注销监听器。所属实例需已绑定事件总线，[event] 为保留的事件类型参数。 */
		@JvmStatic
		@Suppress("UNUSED_PARAMETER")
		fun unregister(owner: AbstractRegistrate<*>, listener: Any, event: Class<out Event>) {
			unregister(owner.modEventBus!!, listener)
		}

		@Synchronized
		private fun unregister(bus: IEventBus, listener: Any) {
			toUnregister.add { bus.unregister(listener) }
		}

		private fun onLoadComplete(event: FMLLoadCompleteEvent) {
			event.enqueueWork {
				toUnregister.forEach(Runnable::run)
				toUnregister.clear()
			}
		}

		private fun <T : Event> addListener(bus: IEventBus, priority: EventPriority, eventClass: Class<T>, listener: Consumer<in T>) {
			bus.addListener(priority, false, eventClass, OneTimeEventReceiver(bus, listener))
		}
	}
}
