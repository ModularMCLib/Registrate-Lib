package com.modularmc.registrate.providers

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.RegistrateLookupFillerProvider

import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.registries.RegistryPatchGenerator
import net.neoforged.fml.LogicalSide
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider

import java.util.concurrent.CompletableFuture

/** 写出所属模组的数据包注册条目，并将新增条目加入下游提供器的注册表查询。 */
open class RegistrateDatapackProvider(parent: AbstractRegistrate<*>, output: PackOutput, provider: CompletableFuture<HolderLookup.Provider>) :
	DatapackBuiltinEntriesProvider(output, RegistryPatchGenerator.createLookup(provider, parent.dataGenInitializer.getDatapackRegistryProviders()), setOf(parent.modid)),
	RegistrateLookupFillerProvider {
	/** 返回补齐本轮数据包注册条目的异步查询。 */
	override fun getFilledProvider(): CompletableFuture<HolderLookup.Provider> = registryProvider

	/** 数据包注册条目作为服务端数据生成。 */
	override fun getSide(): LogicalSide = LogicalSide.SERVER
}
