package com.modularmc.registrate.providers

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.providers.core.RegistrateProvider

import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.neoforged.fml.LogicalSide
import net.neoforged.neoforge.common.data.DataMapProvider

import java.util.concurrent.CompletableFuture

open class RegistrateDataMapProvider(private val parent: AbstractRegistrate<*>, output: PackOutput, lookup: CompletableFuture<HolderLookup.Provider>) :
	DataMapProvider(output, lookup),
	RegistrateProvider {
	private var provider: HolderLookup.Provider? = null

	override fun getSide(): LogicalSide = LogicalSide.SERVER

	override fun gather(provider: HolderLookup.Provider) {
		this.provider = provider
		try {
			parent.genData(ProviderType.DATA_MAP, this)
		} finally {
			this.provider = null
		}
	}

	open fun getProvider(): HolderLookup.Provider = checkNotNull(provider) { "Holder lookup is available during the data map callback" }
}
