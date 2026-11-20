package com.modularmc.registrate.providers

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.RegistrateLookupFillerProvider

import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.registries.RegistryPatchGenerator
import net.neoforged.fml.LogicalSide
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider

import java.util.concurrent.CompletableFuture

open class RegistrateDatapackProvider(parent: AbstractRegistrate<*>, output: PackOutput, provider: CompletableFuture<HolderLookup.Provider>) :
	DatapackBuiltinEntriesProvider(output, RegistryPatchGenerator.createLookup(provider, parent.getDataGenInitializer().getDatapackRegistryProviders()), setOf(parent.modid)),
	RegistrateLookupFillerProvider {
	override fun getFilledProvider(): CompletableFuture<HolderLookup.Provider> = registryProvider

	override fun getSide(): LogicalSide = LogicalSide.SERVER
}
