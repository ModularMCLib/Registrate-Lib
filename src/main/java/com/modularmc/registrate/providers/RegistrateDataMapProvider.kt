package com.modularmc.registrate.providers

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.providers.core.RegistrateProvider

import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.neoforged.fml.LogicalSide
import net.neoforged.neoforge.common.data.DataMapProvider

import java.util.concurrent.CompletableFuture

/** 生成注册条目的数据映射。对应的注册表查询只在本轮生成回调中可用。 */
open class RegistrateDataMapProvider(private val parent: AbstractRegistrate<*>, output: PackOutput, lookup: CompletableFuture<HolderLookup.Provider>) :
	DataMapProvider(output, lookup),
	RegistrateProvider {
	private var provider: HolderLookup.Provider? = null

	/** 数据映射作为服务端数据生成。 */
	override fun getSide(): LogicalSide = LogicalSide.SERVER

	/** 以本轮注册表查询执行数据映射回调，并在回调结束后释放查询引用。 */
	override fun gather(provider: HolderLookup.Provider) {
		this.provider = provider
		try {
			parent.genData(ProviderType.DATA_MAP, this)
		} finally {
			this.provider = null
		}
	}

	/** 获取当前回调使用的注册表查询。在生成回调之外调用会抛出 [IllegalStateException]。 */
	open fun getProvider(): HolderLookup.Provider = checkNotNull(provider) { "Holder lookup is available during the data map callback" }
}
