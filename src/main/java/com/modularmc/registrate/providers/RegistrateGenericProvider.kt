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

class RegistrateGenericProvider @ApiStatus.Internal constructor(
	private val registrate: AbstractRegistrate<*>,
	private val output: PackOutput,
	private val registries: CompletableFuture<HolderLookup.Provider>,
	private val side: LogicalSide,
	private val providerType: ProviderType<RegistrateGenericProvider>,
) : RegistrateProvider {
	private val generators = ArrayList<Generator>()

	fun add(generator: Generator) = apply { generators.add(generator) }

	override fun getSide(): LogicalSide = side

	override fun run(cache: CachedOutput): CompletableFuture<*> {
		generators.clear()
		val data = GeneratorData(output, registries)
		registrate.genData(providerType, this)
		val runs = Array(generators.size) { generators[it].generate(data).run(cache) }
		return CompletableFuture.allOf(*runs)
	}

	override fun getName(): String = "generic_${side.name.lowercase()}_provider"

	@JvmRecord
	data class GeneratorData(val output: PackOutput, val registries: CompletableFuture<HolderLookup.Provider>)

	/** Supplies a provider for the current output and registry lookups. */
	fun interface Generator {
		fun generate(data: GeneratorData): DataProvider
	}
}
