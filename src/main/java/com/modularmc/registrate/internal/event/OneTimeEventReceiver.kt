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

class OneTimeEventReceiver<T : Event>(private val bus: IEventBus, private val listener: Consumer<in T>) : Consumer<T> {
	private val consumed = AtomicBoolean()

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

		@JvmStatic
		fun <T> addModListener(owner: AbstractRegistrate<*>, eventClass: Class<T>, listener: Consumer<in T>) where T : Event, T : IModBusEvent {
			addModListener(owner, EventPriority.NORMAL, eventClass, listener)
		}

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
