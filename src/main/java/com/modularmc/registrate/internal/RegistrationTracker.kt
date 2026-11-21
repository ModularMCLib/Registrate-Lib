package com.modularmc.registrate.internal

import com.modularmc.registrate.internal.util.DebugMarkers
import com.modularmc.registrate.internal.util.RegistrateLogger
import com.modularmc.registrate.localization.EntryLocalization
import com.modularmc.registrate.util.entry.EntryMetadata
import com.modularmc.registrate.util.entry.RegistryEntry
import com.modularmc.registrate.util.nullness.NonNullConsumer
import com.modularmc.registrate.util.nullness.NonNullFunction
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.Registry
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.RegisterEvent

import com.google.common.collect.HashMultimap
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.util.Optional
import java.util.function.BiConsumer

class RegistrationTracker(private val modId: String) {
	private enum class Stage(val description: String) {
		CREATION("creation"),
		INSERTION("registry insertion"),
		CALLBACK("entry callback"),
	}

	private data class EntryKey(val name: String, val registry: ResourceKey<out Registry<*>>)

	private class Registration<R : Any, T : R>(
		val identifier: Identifier,
		val registry: ResourceKey<out Registry<R>>,
		creator: NonNullSupplier<out T>,
		entryFactory: NonNullFunction<DeferredHolder<R, T>, out RegistryEntry<R, T>>,
		val metadata: EntryMetadata,
	) {
		private val creator = creator.lazy()
		val delegate: RegistryEntry<R, T> = entryFactory.apply(DeferredHolder.create(registry, identifier))
		private var callbacks: MutableList<NonNullConsumer<in T>>? = null

		fun addRegisterCallback(callback: NonNullConsumer<in T>) {
			val callbacks = callbacks ?: ArrayList<NonNullConsumer<in T>>(1).also { callbacks = it }
			callbacks.add(callback)
		}

		fun register(event: RegisterEvent) {
			var stage = Stage.CREATION
			try {
				val entry = creator.get()
				stage = Stage.INSERTION
				event.register(registry) { it.register(identifier, entry) }
				stage = Stage.CALLBACK
				callbacks?.forEach { it.accept(entry) }
				callbacks = null
			} catch (error: RuntimeException) {
				throw IllegalStateException(
					"[${identifier.namespace}] Registration of $identifier in ${registry.identifier()} failed during ${stage.description}",
					error,
				)
			}
		}
	}

	private val registrations = Object2ObjectLinkedOpenHashMap<ResourceKey<out Registry<*>>, Object2ObjectLinkedOpenHashMap<String, Registration<*, *>>>()
	private val pendingEntryCallbacks = HashMultimap.create<EntryKey, NonNullConsumer<*>>()
	private val afterRegisterCallbacks = HashMultimap.create<ResourceKey<out Registry<*>>, Runnable>()
	private val completedRegistrations = ObjectOpenHashSet<ResourceKey<out Registry<*>>>()

	fun onRegister(event: RegisterEvent, skipErrors: Boolean, devEnvironment: Boolean) {
		val registry = event.registryKey
		if (!pendingEntryCallbacks.isEmpty) {
			for ((key, callbacks) in pendingEntryCallbacks.asMap()) {
				LOGGER.warn(
					RegistrateLogger.modMessage("Found {} unused register callback(s) for entry {} [{}]. Was the entry ever registered?"),
					modId,
					callbacks.size,
					key.name,
					key.registry.identifier(),
				)
			}
			pendingEntryCallbacks.clear()
			check(!devEnvironment) { "Found unused register callbacks, see logs" }
		}

		val entries = registrations[registry] ?: return
		LOGGER.trace(DebugMarkers.REGISTER, RegistrateLogger.modMessage("Registering {} captured object(s) in {}"), modId, entries.size, registry.identifier())
		for (registration in entries.values) {
			try {
				registration.register(event)
				LOGGER.trace(DebugMarkers.REGISTER, RegistrateLogger.modMessage("Registered {} in {}"), modId, registration.identifier, registry.identifier())
			} catch (error: IllegalStateException) {
				if (!skipErrors) throw error
				LOGGER.error(DebugMarkers.REGISTER, error.message, error)
			}
		}
	}

	fun onRegisterLate(event: RegisterEvent) {
		val registry = event.registryKey
		completedRegistrations.add(registry)
		for (callback in afterRegisterCallbacks.removeAll(registry)) callback.run()
	}

	fun <R : Any, T : R> track(
		name: String,
		registry: ResourceKey<out Registry<R>>,
		creator: NonNullSupplier<out T>,
		entryFactory: NonNullFunction<DeferredHolder<R, T>, out RegistryEntry<R, T>>,
		metadata: EntryMetadata,
	): RegistryEntry<R, T> {
		val id = Identifier.fromNamespaceAndPath(modId, name)
		val entries = registrations[registry]
		require(entries?.containsKey(name) != true) { "[$modId] Duplicate registration $id in ${registry.identifier()}" }

		val registration = Registration(id, registry, creator, entryFactory, metadata)
		LOGGER.trace(DebugMarkers.REGISTER, RegistrateLogger.modMessage("Captured registration {} for registry {}"), modId, id, registry.identifier())
		for (callback in pendingEntryCallbacks.removeAll(EntryKey(name, registry))) {
			@Suppress("UNCHECKED_CAST")
			registration.addRegisterCallback(callback as NonNullConsumer<in T>)
		}
		val row = entries ?: Object2ObjectLinkedOpenHashMap<String, Registration<*, *>>().also { registrations[registry] = it }
		row[name] = registration
		return registration.delegate
	}

	fun getMetadata(key: ResourceKey<*>): EntryMetadata = registration(key.identifier().path, key.registryKey()).metadata

	fun forEachLocalization(action: BiConsumer<RegistryEntry<*, *>, EntryLocalization>) {
		for (row in registrations.values) {
			for (registration in row.values) {
				val localization = registration.metadata.localization() ?: continue
				action.accept(registration.delegate, localization)
			}
		}
	}

	fun <R : Any, T : R> addRegisterCallback(name: String, registry: ResourceKey<out Registry<R>>, callback: NonNullConsumer<in T>) {
		val entry = findRegistration<R, T>(name, registry)
		if (entry == null) {
			pendingEntryCallbacks.put(EntryKey(name, registry), callback)
		} else {
			entry.addRegisterCallback(callback)
		}
	}

	fun <R : Any> addAfterRegisterCallback(registry: ResourceKey<out Registry<R>>, callback: Runnable) {
		afterRegisterCallbacks.put(registry, callback)
	}

	fun <R : Any> isRegistered(registry: ResourceKey<out Registry<R>>): Boolean = registry in completedRegistrations

	@Suppress("UNCHECKED_CAST")
	fun <R : Any, T : R> get(name: String, registry: ResourceKey<out Registry<R>>): RegistryEntry<R, T> = registration(name, registry).delegate as RegistryEntry<R, T>

	fun <R : Any, T : R> getOptional(name: String, registry: ResourceKey<out Registry<R>>): Optional<RegistryEntry<R, T>> = Optional.ofNullable(findRegistration<R, T>(name, registry)?.delegate)

	@Suppress("UNCHECKED_CAST")
	fun <R : Any, T : R> getAll(registry: ResourceKey<out Registry<R>>): Collection<RegistryEntry<R, T>> = registrations[registry]?.values?.map { it.delegate as RegistryEntry<R, T> } ?: emptyList()

	private fun registration(name: String, registry: ResourceKey<out Registry<*>>): Registration<*, *> =
		registrations[registry]?.get(name) ?: throw IllegalArgumentException("Unknown registration $name for type ${registry.identifier()}")

	@Suppress("UNCHECKED_CAST")
	private fun <R : Any, T : R> findRegistration(name: String, registry: ResourceKey<out Registry<R>>): Registration<R, T>? = registrations[registry]?.get(name) as Registration<R, T>?

	companion object {
		private val LOGGER = RegistrateLogger.registration()
	}
}
