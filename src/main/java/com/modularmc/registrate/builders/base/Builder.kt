package com.modularmc.registrate.builders.base

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.DataGenContext
import com.modularmc.registrate.providers.core.GeneratorType
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.util.entry.RegistryEntry
import com.modularmc.registrate.util.nullness.NonNullBiConsumer
import com.modularmc.registrate.util.nullness.NonNullBiFunction
import com.modularmc.registrate.util.nullness.NonNullConsumer
import com.modularmc.registrate.util.nullness.NonNullFunction
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.neoforged.neoforge.registries.datamaps.DataMapType

/** Collects entry declarations during mod setup and submits them to the owning Registrate. */
interface Builder<R : Any, T : R, P : Any, S : Builder<R, T, P, S>> : NonNullSupplier<RegistryEntry<R, T>> {
	val owner: AbstractRegistrate<*>
	val parent: P
	val name: String
	val registryKey: ResourceKey<out Registry<R>>

	/** Submits the entry and returns its deferred holder. Each ID can be submitted once per registry. */
	fun register(): RegistryEntry<R, T>

	override fun get(): RegistryEntry<R, T> = owner.get(name, registryKey)

	/** The registered object, available after its holder binds. */
	val entry: T
		get() = get().get()

	fun asSupplier(): NonNullSupplier<T>

	@Suppress("UNCHECKED_CAST")
	fun <D : Any> setData(type: GeneratorType<out D>, cons: NonNullBiConsumer<DataGenContext<R, T>, D>): S {
		owner.setDataGenerator(this, type) { cons.accept(DataGenContext.from(this), it) }
		return this as S
	}

	@Suppress("UNCHECKED_CAST")
	fun <D : Any> removeData(type: GeneratorType<out D>): S {
		owner.removeDataGenerator(this, type)
		return this as S
	}

	@Suppress("UNCHECKED_CAST")
	fun <D : Any> addMiscData(type: GeneratorType<out D>, cons: NonNullConsumer<out D>): S {
		owner.addDataGenerator(type, cons)
		return this as S
	}

	fun <D : Any> dataMap(type: DataMapType<R, D>, value: D): S = dataMap(type, NonNullFunction { value })

	fun <D : Any> dataMap(type: DataMapType<R, D>, factory: NonNullFunction<DataGenContext<R, T>, D>): S = dataMap(type, NonNullBiFunction { context, _ -> factory.apply(context) })

	@Suppress("UNCHECKED_CAST")
	fun <D : Any> dataMap(type: DataMapType<R, D>, factory: NonNullBiFunction<DataGenContext<R, T>, HolderLookup.Provider, D>): S {
		owner.addDataGenerator(ProviderType.DATA_MAP) { provider ->
			val context = DataGenContext.from(this)
			provider.builder(type).add(context.id, factory.apply(context, provider.getProvider()), false)
		}
		return this as S
	}

	/** Adds a callback that runs after the object is inserted into its registry. */
	@Suppress("UNCHECKED_CAST")
	fun onRegister(callback: NonNullConsumer<in T>): S {
		owner.addRegisterCallback<R, T>(name, registryKey, callback)
		return this as S
	}

	/** Runs the callback after this entry binds and the dependency registry is complete. */
	fun <OR : Any> onRegisterAfter(dependencyType: ResourceKey<out Registry<OR>>, callback: NonNullConsumer<in T>): S = onRegister { value ->
		if (owner.isRegistered(dependencyType)) {
			callback.accept(value)
		} else {
			owner.addRegisterCallback(dependencyType) { callback.accept(value) }
		}
	}

	@Suppress("UNCHECKED_CAST")
	fun <R2 : Any, T2 : R2, P2 : Any, S2 : Builder<R2, T2, P2, S2>> transform(func: NonNullFunction<S, S2>): S2 = func.apply(this as S)

	/** Registers the entry and returns the parent so the enclosing chain can continue. */
	fun build(): P {
		register()
		return parent
	}
}
