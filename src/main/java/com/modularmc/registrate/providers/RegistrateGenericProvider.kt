package com.modularmc.registrate.providers

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.providers.core.RegistrateProvider

import net.minecraft.core.HolderLookup
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.neoforged.fml.LogicalSide

import org.jetbrains.annotations.ApiStatus

import java.util.concurrent.CompletableFuture

/** 通过模组回调创建并运行一组自定义数据提供器。每轮生成都会重新收集子任务。 */
class RegistrateGenericProvider @ApiStatus.Internal constructor(
	private val registrate: AbstractRegistrate<*>,
	private val output: PackOutput,
	private val registries: CompletableFuture<HolderLookup.Provider>,
	private val side: LogicalSide,
	private val providerType: ProviderType<RegistrateGenericProvider>,
) : RegistrateProvider {
	private val generators = ArrayList<Generator>()

	/** 在本轮生成回调中追加一个子任务工厂，并返回当前提供器。 */
	fun add(generator: Generator) = apply { generators.add(generator) }

	/** 返回创建提供器时配置的逻辑侧。 */
	override fun getSide(): LogicalSide = side

	/** 重新收集子任务，使用同一输出和注册表上下文执行它们，并等待全部完成。 */
	override fun run(cache: CachedOutput): CompletableFuture<*> {
		generators.clear()
		val data = GeneratorData(output, registries)
		registrate.genData(providerType, this)
		val runs = Array(generators.size) { generators[it].generate(data).run(cache) }
		return CompletableFuture.allOf(*runs)
	}

	/** 返回含有逻辑侧的任务名称，供数据生成日志显示。 */
	override fun getName(): String = "generic_${side.name.lowercase()}_provider"

	/** 创建子任务时传入的输出位置和异步注册表查询。 */
	@JvmRecord
	data class GeneratorData(val output: PackOutput, val registries: CompletableFuture<HolderLookup.Provider>)

	/** 根据当前输出和注册表查询创建数据提供器，供数据生成流程调用。 */
	fun interface Generator {
		/** 用 [data] 所含的输出位置和查询创建一个子任务，交由当前数据生成流程运行。 */
		fun generate(data: GeneratorData): DataProvider
	}
}
