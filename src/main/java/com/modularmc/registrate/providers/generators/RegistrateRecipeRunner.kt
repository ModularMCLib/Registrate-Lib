package com.modularmc.registrate.providers.generators

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.RegistrateProvider

import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.neoforged.fml.LogicalSide

import java.util.concurrent.CompletableFuture

/** 启动所属模组的配方生成，为原版输出和模组回调之间提供配方生成器。 */
open class RegistrateRecipeRunner(val owner: AbstractRegistrate<*>, output: PackOutput, completableFuture: CompletableFuture<HolderLookup.Provider>) :
	RecipeProvider.Runner(output, completableFuture),
	RegistrateProvider {
	internal var provider: RegistrateRecipeProvider? = null

	/** 在注册表查询可用后，为本轮配方输出创建生成器。 */
	override fun createRecipeProvider(holderLookupProvider: HolderLookup.Provider, recipeOutput: RecipeOutput): RecipeProvider = RegistrateRecipeProvider(this, holderLookupProvider, recipeOutput)

	/** 返回配方生成任务在日志中显示的名称。 */
	override fun getName(): String = "Recipes"

	/** 配方作为服务端数据生成。 */
	override fun getSide(): LogicalSide = LogicalSide.SERVER

	val recipeProvider: RegistrateRecipeProvider
		get() = checkNotNull(provider) { "Recipe provider is only available during recipe generation" }
}
