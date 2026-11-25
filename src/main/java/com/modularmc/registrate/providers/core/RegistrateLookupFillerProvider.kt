package com.modularmc.registrate.providers.core

import net.minecraft.core.HolderLookup

import java.util.concurrent.CompletableFuture

/** 为本轮数据生成的下游提供器提供包含当前生成结果的注册表查询。 */
interface RegistrateLookupFillerProvider : RegistrateProvider {
	/** 返回本轮生成内容补齐后的异步注册表查询。依赖提供器会使用这个查询。 */
	fun getFilledProvider(): CompletableFuture<HolderLookup.Provider>
}
