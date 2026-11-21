package com.modularmc.registrate.providers.generators

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.RegistrateProvider

import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.neoforged.fml.LogicalSide

import java.util.concurrent.CompletableFuture

open class RegistrateRecipeRunner(val owner: AbstractRegistrate<*>, output: PackOutput, completableFuture: CompletableFuture<HolderLookup.Provider>) :
	RecipeProvider.Runner(output, completableFuture),
	RegistrateProvider {
	internal var provider: RegistrateRecipeProvider? = null

	override fun createRecipeProvider(holderLookupProvider: HolderLookup.Provider, recipeOutput: RecipeOutput): RecipeProvider = RegistrateRecipeProvider(this, holderLookupProvider, recipeOutput)

	override fun getName(): String = "Recipes"

	override fun getSide(): LogicalSide = LogicalSide.SERVER

	val recipeProvider: RegistrateRecipeProvider
		get() = checkNotNull(provider) { "Recipe provider is only available during recipe generation" }
}
