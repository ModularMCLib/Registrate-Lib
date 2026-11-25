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
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.RegisterEvent

import com.google.common.collect.HashMultimap
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.util.Optional
import java.util.function.BiConsumer

/** 在同一模组内按注册表暂存条目，管理绑定阶段、条目元数据和注册回调。 */
class RegistrationTracker(private val modId: String) {
	private enum class Stage(val description: String) {
		CREATION("creation"),
		INSERTION("registry insertion"),
		CALLBACK("entry callback"),
	}

	private data class EntryKey(val name: String, val registry: ResourceKey<out Registry<*>>)

	private class Registration<R : Any, T : R>(
		val identifier: ResourceLocation,
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
					"[${identifier.namespace}] Registration of $identifier in ${registry.location()} failed during ${stage.description}",
					error,
				)
			}
		}
	}

	private val registrations = Object2ObjectLinkedOpenHashMap<ResourceKey<out Registry<*>>, Object2ObjectLinkedOpenHashMap<String, Registration<*, *>>>()
	private val pendingEntryCallbacks = HashMultimap.create<EntryKey, NonNullConsumer<*>>()
	private val afterRegisterCallbacks = HashMultimap.create<ResourceKey<out Registry<*>>, Runnable>()
	private val completedRegistrations = ObjectOpenHashSet<ResourceKey<out Registry<*>>>()

	/**
	 * 在普通优先级处理当前注册表，按提交顺序创建、写入并绑定条目。
	 * @param event 当前注册表的注册事件
	 * @param skipErrors 条目注册失败时是否记录异常后继续处理下一项
	 * @param devEnvironment 开发环境下发现未使用的条目回调时立即抛出异常
	 */
	fun onRegister(event: RegisterEvent, skipErrors: Boolean, devEnvironment: Boolean) {
		val registry = event.registryKey
		if (!pendingEntryCallbacks.isEmpty) {
			for ((key, callbacks) in pendingEntryCallbacks.asMap()) {
				LOGGER.warn(
					RegistrateLogger.modMessage("Found {} unused register callback(s) for entry {} [{}]. Was the entry ever registered?"),
					modId,
					callbacks.size,
					key.name,
					key.registry.location(),
				)
			}
			pendingEntryCallbacks.clear()
			check(!devEnvironment) { "Found unused register callbacks, see logs" }
		}

		val entries = registrations[registry] ?: return
		LOGGER.trace(DebugMarkers.REGISTER, RegistrateLogger.modMessage("Registering {} captured object(s) in {}"), modId, entries.size, registry.location())
		for (registration in entries.values) {
			try {
				registration.register(event)
				LOGGER.trace(DebugMarkers.REGISTER, RegistrateLogger.modMessage("Registered {} in {}"), modId, registration.identifier, registry.location())
			} catch (error: IllegalStateException) {
				if (!skipErrors) throw error
				LOGGER.error(DebugMarkers.REGISTER, error.message, error)
			}
		}
	}

	/** 在最低优先级标记注册表完成，并执行等待此注册表的回调。 */
	fun onRegisterLate(event: RegisterEvent) {
		val registry = event.registryKey
		completedRegistrations.add(registry)
		for (callback in afterRegisterCallbacks.removeAll(registry)) callback.run()
	}

	/**
	 * 暂存同名条目的对象工厂、引用和元数据。返回的引用要等注册事件触发后才绑定。
	 * @param name 当前模组命名空间下的条目名称
	 * @param registry 目标注册表
	 * @param creator 写入注册表时调用的对象工厂
	 * @param entryFactory 用延迟引用构造条目包装对象的工厂
	 * @param metadata 随条目保存的数据生成回调和本地化声明
	 * @return 尚未绑定的条目引用
	 * @throws IllegalArgumentException 同一注册表已存在同名条目
	 */
	fun <R : Any, T : R> track(
		name: String,
		registry: ResourceKey<out Registry<R>>,
		creator: NonNullSupplier<out T>,
		entryFactory: NonNullFunction<DeferredHolder<R, T>, out RegistryEntry<R, T>>,
		metadata: EntryMetadata,
	): RegistryEntry<R, T> {
		val id = ResourceLocation.fromNamespaceAndPath(modId, name)
		val entries = registrations[registry]
		require(entries?.containsKey(name) != true) { "[$modId] Duplicate registration $id in ${registry.location()}" }

		val registration = Registration(id, registry, creator, entryFactory, metadata)
		LOGGER.trace(DebugMarkers.REGISTER, RegistrateLogger.modMessage("Captured registration {} for registry {}"), modId, id, registry.location())
		for (callback in pendingEntryCallbacks.removeAll(EntryKey(name, registry))) {
			@Suppress("UNCHECKED_CAST")
			registration.addRegisterCallback(callback as NonNullConsumer<in T>)
		}
		val row = entries ?: Object2ObjectLinkedOpenHashMap<String, Registration<*, *>>().also { registrations[registry] = it }
		row[name] = registration
		return registration.delegate
	}

	/** 查询指定注册键的元数据，未提交该条目时抛出 [IllegalArgumentException]。 */
	fun getMetadata(key: ResourceKey<*>): EntryMetadata = registration(key.location().path, key.registryKey()).metadata

	/** 在调用线程上逐一处理已声明本地化的条目，向回调传入条目引用和声明。 */
	fun forEachLocalization(action: BiConsumer<RegistryEntry<*, *>, EntryLocalization>) {
		for (row in registrations.values) {
			for (registration in row.values) {
				val localization = registration.metadata.localization() ?: continue
				action.accept(registration.delegate, localization)
			}
		}
	}

	/** 添加在条目注册时执行的回调。条目尚未提交时先按注册表和名称暂存。 */
	fun <R : Any, T : R> addRegisterCallback(name: String, registry: ResourceKey<out Registry<R>>, callback: NonNullConsumer<in T>) {
		val entry = findRegistration<R, T>(name, registry)
		if (entry == null) {
			pendingEntryCallbacks.put(EntryKey(name, registry), callback)
		} else {
			entry.addRegisterCallback(callback)
		}
	}

	/** 暂存注册表完成后运行的回调。应在该注册表的注册完成事件之前调用。 */
	fun <R : Any> addAfterRegisterCallback(registry: ResourceKey<out Registry<R>>, callback: Runnable) {
		afterRegisterCallbacks.put(registry, callback)
	}

	/** 判断指定注册表是否已进入注册完成阶段。 */
	fun <R : Any> isRegistered(registry: ResourceKey<out Registry<R>>): Boolean = registry in completedRegistrations

	/** 按注册表和名称查找已提交条目的引用。缺少该条目时抛出 [IllegalArgumentException]。 */
	@Suppress("UNCHECKED_CAST")
	fun <R : Any, T : R> get(name: String, registry: ResourceKey<out Registry<R>>): RegistryEntry<R, T> = registration(name, registry).delegate as RegistryEntry<R, T>

	/** 按注册表和名称查找条目引用，没有对应条目时返回空值。 */
	fun <R : Any, T : R> getOptional(name: String, registry: ResourceKey<out Registry<R>>): Optional<RegistryEntry<R, T>> = Optional.ofNullable(findRegistration<R, T>(name, registry)?.delegate)

	/** 收集当前模组提交到指定注册表的引用，保留提交顺序。 */
	@Suppress("UNCHECKED_CAST")
	fun <R : Any, T : R> getAll(registry: ResourceKey<out Registry<R>>): Collection<RegistryEntry<R, T>> = registrations[registry]?.values?.map { it.delegate as RegistryEntry<R, T> } ?: emptyList()

	private fun registration(name: String, registry: ResourceKey<out Registry<*>>): Registration<*, *> =
		registrations[registry]?.get(name) ?: throw IllegalArgumentException("Unknown registration $name for type ${registry.location()}")

	@Suppress("UNCHECKED_CAST")
	private fun <R : Any, T : R> findRegistration(name: String, registry: ResourceKey<out Registry<R>>): Registration<R, T>? = registrations[registry]?.get(name) as Registration<R, T>?

	companion object {
		private val LOGGER = RegistrateLogger.registration()
	}
}
