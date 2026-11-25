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

/**
 * 在模组初始化期间收集条目配置，并将其提交给所属 Registrate。
 * 提交前可继续链式配置，条目对象在延迟引用绑定后可读。
 *
 * @param R 注册表中的基础类型
 * @param T 条目的具体类型
 * @param P 构建链的父级类型
 * @param S 当前构建器类型
 */
interface Builder<R : Any, T : R, P : Any, S : Builder<R, T, P, S>> : NonNullSupplier<RegistryEntry<R, T>> {
	val owner: AbstractRegistrate<*>
	val parent: P
	val name: String
	val registryKey: ResourceKey<out Registry<R>>

	/** 提交条目并返回延迟引用。同一注册表内每个资源标识只能提交一次。 */
	fun register(): RegistryEntry<R, T>

	/** 返回已经提交给 Registrate 的条目引用。条目尚未提交时抛出 [IllegalArgumentException]。 */
	override fun get(): RegistryEntry<R, T> = owner.get(name, registryKey)

	/** 读取已注册的对象。调用前须等该条目的延迟引用完成绑定。 */
	val entry: T
		get() = get().get()

	/** 返回延迟取得实际游戏对象的取值器。可在注册前保存取值器，实际取值须等待条目绑定。 */
	fun asSupplier(): NonNullSupplier<T>

	/** 为当前条目设置指定类型的生成回调。重复设置会替换此前为该条目配置的同类型回调。 */
	@Suppress("UNCHECKED_CAST")
	fun <D : Any> setData(type: GeneratorType<out D>, cons: NonNullBiConsumer<DataGenContext<R, T>, D>): S {
		owner.setDataGenerator(this, type) { cons.accept(DataGenContext.from(this), it) }
		return this as S
	}

	/** 移除当前条目指定类型的生成回调。 */
	@Suppress("UNCHECKED_CAST")
	fun <D : Any> removeData(type: GeneratorType<out D>): S {
		owner.removeDataGenerator(this, type)
		return this as S
	}

	/** 为当前模组追加独立的生成回调，回调只接收匹配类型的生成器。 */
	@Suppress("UNCHECKED_CAST")
	fun <D : Any> addMiscData(type: GeneratorType<out D>, cons: NonNullConsumer<out D>): S {
		owner.addDataGenerator(type, cons)
		return this as S
	}

	/** 为当前条目的数据映射设置固定值，由数据生成阶段写出。 */
	fun <D : Any> dataMap(type: DataMapType<R, D>, value: D): S = dataMap(type) { value }

	/** 在数据生成期间根据当前条目的上下文计算数据映射值。 */
	fun <D : Any> dataMap(type: DataMapType<R, D>, factory: NonNullFunction<DataGenContext<R, T>, D>): S = dataMap(type) { context, _ ->
		factory.apply(
			context,
		)
	}

	/**
	 * 在数据生成期间用条目信息和本轮注册表查询计算数据映射值。
	 * @param type 要写入的数据映射类型
	 * @param factory 接收条目上下文和注册表查询的工厂
	 * @return 当前构建器
	 */
	@Suppress("UNCHECKED_CAST")
	fun <D : Any> dataMap(type: DataMapType<R, D>, factory: NonNullBiFunction<DataGenContext<R, T>, HolderLookup.Provider, D>): S {
		owner.addDataGenerator(ProviderType.DATA_MAP) { provider ->
			val context = DataGenContext.from(this)
			provider.builder(type).add(context.id, factory.apply(context, provider.getProvider()), false)
		}
		return this as S
	}

	/** 添加在对象写入所属注册表后执行的回调。 */
	@Suppress("UNCHECKED_CAST")
	fun onRegister(callback: NonNullConsumer<in T>): S {
		owner.addRegisterCallback(name, registryKey, callback)
		return this as S
	}

	/** 当本条目已绑定且依赖的注册表完成注册后执行回调。 */
	fun <OR : Any> onRegisterAfter(dependencyType: ResourceKey<out Registry<OR>>, callback: NonNullConsumer<in T>): S = onRegister { value ->
		if (owner.isRegistered(dependencyType)) {
			callback.accept(value)
		} else {
			owner.addRegisterCallback(dependencyType) { callback.accept(value) }
		}
	}

	/** 将当前构建器交给 [func] 转换为另一个类型的构建器，并继续调用其构建链。 */
	@Suppress("UNCHECKED_CAST")
	fun <R2 : Any, T2 : R2, P2 : Any, S2 : Builder<R2, T2, P2, S2>> transform(func: NonNullFunction<S, S2>): S2 = func.apply(this as S)

	/** 提交条目并返回父级对象，以便继续外层构建链。 */
	fun build(): P {
		register()
		return parent
	}
}
