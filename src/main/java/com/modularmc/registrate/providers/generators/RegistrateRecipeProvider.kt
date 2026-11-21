// Preserve Java's public static bridges to inherited protected static methods.
@file:Suppress("ACCIDENTAL_OVERRIDE")

package com.modularmc.registrate.providers.generators

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.providers.core.RegistrateProvider
import com.modularmc.registrate.util.DataIngredient
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.advancements.Advancement
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.advancements.Criterion
import net.minecraft.advancements.critereon.EnterBlockTrigger
import net.minecraft.advancements.critereon.InventoryChangeTrigger
import net.minecraft.advancements.critereon.ItemPredicate
import net.minecraft.advancements.critereon.MinMaxBounds
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.BlockFamily
import net.minecraft.data.CachedOutput
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeBuilder
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.data.recipes.ShapedRecipeBuilder
import net.minecraft.data.recipes.ShapelessRecipeBuilder
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder
import net.minecraft.data.recipes.SingleItemRecipeBuilder
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.item.Item
import net.minecraft.world.item.crafting.AbstractCookingRecipe
import net.minecraft.world.item.crafting.BlastingRecipe
import net.minecraft.world.item.crafting.CampfireCookingRecipe
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.SmeltingRecipe
import net.minecraft.world.item.crafting.SmokingRecipe
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.block.Block
import net.neoforged.fml.LogicalSide
import net.neoforged.neoforge.common.Tags
import net.neoforged.neoforge.common.conditions.ICondition

import com.google.common.collect.ImmutableMap

import java.util.Objects
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import javax.annotation.CheckReturnValue
import javax.annotation.processing.Generated

open class RegistrateRecipeProvider(private val owner: AbstractRegistrate<*>, output: PackOutput, private val lookupProvider: CompletableFuture<HolderLookup.Provider>) :
	RecipeProvider(output, lookupProvider),
	RegistrateProvider,
	RecipeOutput {
	private var callback: RecipeOutput? = null

	fun registries() = Objects.requireNonNull(
		lookupProvider.getNow(null),
		"Recipe registry lookup is not ready",
	)

	fun itemLookup() = registries().lookupOrThrow(Registries.ITEM)

	fun blockLookup() = registries().lookupOrThrow(Registries.BLOCK)

	fun entityLookup() = registries().lookupOrThrow(Registries.ENTITY_TYPE)

	fun <T : Any> resolve(key: ResourceKey<T>): Holder<T> = registries().lookupOrThrow<T>(key.registryKey()).getOrThrow(key)

	override fun getSide(): LogicalSide = LogicalSide.SERVER

	override fun accept(id: ResourceLocation, recipe: Recipe<*>, advancement: AdvancementHolder?, vararg conditions: ICondition) {
		recipeOutput().accept(id, recipe, advancement, *conditions)
	}

	override fun advancement(): Advancement.Builder = recipeOutput().advancement()

	private fun recipeOutput() = Objects.requireNonNull(
		callback,
		"Recipe output is only available inside a recipe callback",
	)!!

	override fun buildRecipes(output: RecipeOutput) {
		callback = output
		try {
			owner.genData<RegistrateRecipeProvider>(ProviderType.RECIPE, this)
		} finally {
			callback = null
		}
	}

	fun shaped(category: RecipeCategory, result: ItemLike): ShapedRecipeBuilder = ShapedRecipeBuilder.shaped(category, result)

	fun shaped(category: RecipeCategory, result: ItemLike, count: Int): ShapedRecipeBuilder = ShapedRecipeBuilder.shaped(category, result, count)

	fun shapeless(category: RecipeCategory, result: ItemLike): ShapelessRecipeBuilder = ShapelessRecipeBuilder.shapeless(category, result)

	fun shapeless(category: RecipeCategory, result: ItemLike, count: Int): ShapelessRecipeBuilder = ShapelessRecipeBuilder.shapeless(category, result, count)

	fun safeId(id: ResourceLocation): ResourceLocation = ResourceLocation.fromNamespaceAndPath(owner.modid, safeName(id))

	fun safeId(source: DataIngredient): ResourceLocation = safeId(source.id)

	fun safeId(registryEntry: ItemLike): ResourceLocation = safeId(BuiltInRegistries.ITEM.getKey(registryEntry.asItem()))

	fun safeName(id: ResourceLocation): String = id.path.replace('/', '_')

	fun safeName(source: DataIngredient): String = safeName(source.id)

	fun safeName(registryEntry: ItemLike): String = safeName(BuiltInRegistries.ITEM.getKey(registryEntry.asItem()))

	fun <T : ItemLike, S : AbstractCookingRecipe> cooking(
		source: DataIngredient,
		category: RecipeCategory,
		result: Supplier<out T>,
		experience: Float,
		cookingTime: Int,
		serializer: RecipeSerializer<S>,
		factory: AbstractCookingRecipe.Factory<S>,
	) {
		cooking(
			source,
			category,
			result,
			experience,
			cookingTime,
			COOKING_TYPE_NAMES[serializer]!!,
			serializer,
			factory,
		)
	}

	fun <T : ItemLike, S : AbstractCookingRecipe> cooking(
		source: DataIngredient,
		category: RecipeCategory,
		result: Supplier<out T>,
		experience: Float,
		cookingTime: Int,
		typeName: String,
		serializer: RecipeSerializer<S>,
		factory: AbstractCookingRecipe.Factory<S>,
	) {
		SimpleCookingRecipeBuilder.generic<S>(
			source.toVanilla(),
			category,
			result.get(),
			experience,
			cookingTime,
			serializer,
			factory,
		)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeId(result.get()).toString() + "_from_" + safeName(source) + "_" + typeName)
	}

	fun <T : ItemLike> smelting(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, experience: Float) {
		smelting(source, category, result, experience, DEFAULT_SMELT_TIME)
	}

	fun <T : ItemLike> smelting(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, experience: Float, cookingTime: Int) {
		cooking(
			source,
			category,
			result,
			experience,
			cookingTime,
			RecipeSerializer.SMELTING_RECIPE,
			::SmeltingRecipe,
		)
	}

	fun <T : ItemLike> blasting(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, experience: Float) {
		blasting(source, category, result, experience, DEFAULT_BLAST_TIME)
	}

	fun <T : ItemLike> blasting(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, experience: Float, cookingTime: Int) {
		cooking(
			source,
			category,
			result,
			experience,
			cookingTime,
			RecipeSerializer.BLASTING_RECIPE,
			::BlastingRecipe,
		)
	}

	fun <T : ItemLike> smoking(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, experience: Float) {
		smoking(source, category, result, experience, DEFAULT_SMOKE_TIME)
	}

	fun <T : ItemLike> smoking(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, experience: Float, cookingTime: Int) {
		cooking(
			source,
			category,
			result,
			experience,
			cookingTime,
			RecipeSerializer.SMOKING_RECIPE,
			::SmokingRecipe,
		)
	}

	fun <T : ItemLike> campfire(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, experience: Float) {
		campfire(source, category, result, experience, DEFAULT_CAMPFIRE_TIME)
	}

	fun <T : ItemLike> campfire(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, experience: Float, cookingTime: Int) {
		cooking(
			source,
			category,
			result,
			experience,
			cookingTime,
			RecipeSerializer.CAMPFIRE_COOKING_RECIPE,
			::CampfireCookingRecipe,
		)
	}

	fun <T : ItemLike> stonecutting(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>) {
		stonecutting(source, category, result, 1)
	}

	fun <T : ItemLike> stonecutting(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, resultAmount: Int) {
		SingleItemRecipeBuilder.stonecutting(source.toVanilla(), category, result.get(), resultAmount)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeId(result.get()).toString() + "_from_" + safeName(source) + "_stonecutting")
	}

	fun <T : ItemLike> smeltingAndBlasting(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, xp: Float) {
		smelting(source, category, result, xp)
		blasting(source, category, result, xp)
	}

	fun <T : ItemLike> food(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, xp: Float) {
		smelting(source, category, result, xp)
		smoking(source, category, result, xp)
		campfire(source, category, result, xp)
	}

	fun <T : ItemLike> square(source: DataIngredient, category: RecipeCategory, output: Supplier<out T>, small: Boolean) {
		val builder = ShapedRecipeBuilder.shaped(category, output.get())
			.define('X', source.toVanilla())
		if (small) {
			builder.pattern("XX").pattern("XX")
		} else {
			builder.pattern("XXX").pattern("XXX").pattern("XXX")
		}
		builder.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeId(output.get()))
	}

	/**
	 * @param <T>
	 * @param source
	 * @param output
	</T> */
	@Deprecated(
		"""Broken, use {@link #storage(NonNullSupplier, RecipeCategory, NonNullSupplier)} or
				{@link #storage(DataIngredient, RecipeCategory, NonNullSupplier, DataIngredient, NonNullSupplier)}.""",
	)
	fun <T : ItemLike> storage(source: DataIngredient, category: RecipeCategory, output: NonNullSupplier<out T>) {
		square(source, category, output, false)
		// This is backwards, but leaving in for binary compat
		singleItemUnfinished(source, category, output, 1, 9)
			.save(this, safeId(source).toString() + "_from_" + safeName(output.get()))
	}

	fun <T : ItemLike> storage(source: NonNullSupplier<out T>, category: RecipeCategory, output: NonNullSupplier<out T>) {
		storage(DataIngredient.items(source), category, source, DataIngredient.items(output), output)
	}

	fun <T : ItemLike> storage(sourceIngredient: DataIngredient, category: RecipeCategory, source: NonNullSupplier<out T>, outputIngredient: DataIngredient, output: NonNullSupplier<out T>) {
		square(sourceIngredient, category, output, false)
		singleItemUnfinished(outputIngredient, category, source, 1, 9)
			.save(this, safeId(sourceIngredient).toString() + "_from_" + safeName(output.get()))
	}

	@CheckReturnValue
	fun <T : ItemLike> singleItemUnfinished(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, required: Int, amount: Int): ShapelessRecipeBuilder =
		ShapelessRecipeBuilder.shapeless(category, result.get(), amount)
			.requires(source.toVanilla(), required)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))

	fun <T : ItemLike> singleItem(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, required: Int, amount: Int) {
		singleItemUnfinished(source, category, result, required, amount).save(this, safeId(result.get()))
	}

	fun <T : ItemLike> planks(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>) {
		singleItemUnfinished(source, category, result, 1, 4)
			.group("planks")
			.save(this, safeId(result.get()))
	}

	fun <T : ItemLike> stairs(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, group: String?, stone: Boolean) {
		ShapedRecipeBuilder.shaped(category, result.get(), 4)
			.pattern("X  ").pattern("XX ").pattern("XXX")
			.define('X', source.toVanilla())
			.group(group)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeId(result.get()))
		if (stone) {
			stonecutting(source, category, result)
		}
	}

	fun <T : ItemLike> slab(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, group: String?, stone: Boolean) {
		ShapedRecipeBuilder.shaped(category, result.get(), 6)
			.pattern("XXX")
			.define('X', source.toVanilla())
			.group(group)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeId(result.get()))
		if (stone) {
			stonecutting(source, category, result, 2)
		}
	}

	fun <T : ItemLike> fence(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, group: String?) {
		ShapedRecipeBuilder.shaped(category, result.get(), 3)
			.pattern("W#W").pattern("W#W")
			.define('W', source.toVanilla())
			.define('#', Tags.Items.RODS_WOODEN)
			.group(group)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeId(result.get()))
	}

	fun <T : ItemLike> fenceGate(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, group: String?) {
		ShapedRecipeBuilder.shaped(category, result.get())
			.pattern("#W#").pattern("#W#")
			.define('W', source.toVanilla())
			.define('#', Tags.Items.RODS_WOODEN)
			.group(group)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeId(result.get()))
	}

	fun <T : ItemLike> wall(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>) {
		ShapedRecipeBuilder.shaped(category, result.get(), 6)
			.pattern("XXX").pattern("XXX")
			.define('X', source.toVanilla())
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeId(result.get()))
		stonecutting(source, category, result)
	}

	fun <T : ItemLike> door(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, group: String?) {
		ShapedRecipeBuilder.shaped(category, result.get(), 3)
			.pattern("XX").pattern("XX").pattern("XX")
			.define('X', source.toVanilla())
			.group(group)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeId(result.get()))
	}

	fun <T : ItemLike> trapDoor(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, group: String?) {
		ShapedRecipeBuilder.shaped(category, result.get(), 2)
			.pattern("XXX").pattern("XXX")
			.define('X', source.toVanilla())
			.group(group)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeId(result.get()))
	}

	// @formatter:off
	// GENERATED OVERRIDE START

	/** Generated override to expose protected method: [RecipeProvider.buildAdvancement]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
	public override fun buildAdvancement(output: CachedOutput, registries: HolderLookup.Provider, advancement: AdvancementHolder): CompletableFuture<*> =
		super.buildAdvancement(output, registries, advancement)

	/** Generated override to expose protected method: [RecipeProvider.buildAdvancement]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
	public override fun buildAdvancement(output: CachedOutput, registries: HolderLookup.Provider, advancement: AdvancementHolder, vararg conditions: ICondition): CompletableFuture<*> =
		super.buildAdvancement(output, registries, advancement, *conditions)

	/** Generated override to expose protected method: [RecipeProvider.buildRecipes]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
	public override fun buildRecipes(output: RecipeOutput, holderLookup: HolderLookup.Provider) = super.buildRecipes(output, holderLookup)

	/** Generated override to expose protected method: [RecipeProvider.generateForEnabledBlockFamilies]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
	public override fun generateForEnabledBlockFamilies(enabledFeatures: RecipeOutput, flagSet: FeatureFlagSet) = super.generateForEnabledBlockFamilies(enabledFeatures, flagSet)
	// GENERATED OVERRIDE END

	companion object {
		const val DEFAULT_SMELT_TIME: Int = 200
		const val DEFAULT_BLAST_TIME: Int = DEFAULT_SMELT_TIME / 2
		const val DEFAULT_SMOKE_TIME: Int = DEFAULT_BLAST_TIME
		const val DEFAULT_CAMPFIRE_TIME: Int = DEFAULT_SMELT_TIME * 3

		private val COOKING_TYPE_NAMES: ImmutableMap<RecipeSerializer<out AbstractCookingRecipe>, String> = ImmutableMap.builder<RecipeSerializer<out AbstractCookingRecipe>, String>()
			.put(RecipeSerializer.SMELTING_RECIPE, "smelting")
			.put(RecipeSerializer.BLASTING_RECIPE, "blasting")
			.put(RecipeSerializer.SMOKING_RECIPE, "smoking")
			.put(RecipeSerializer.CAMPFIRE_COOKING_RECIPE, "campfire")
			.build()

		// GENERATED STATIC START

		/** Generated override to expose protected method: [RecipeProvider.netheriteSmithing]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun netheriteSmithing(recipeOutput: RecipeOutput, ingredientItem: Item, category: RecipeCategory, resultItem: Item) {
			RecipeProvider.netheriteSmithing(recipeOutput, ingredientItem, category, resultItem)
		}

		/** Generated override to expose protected method: [RecipeProvider.trimSmithing]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun trimSmithing(recipeOutput: RecipeOutput, ingredientItem: Item, location: ResourceLocation) {
			RecipeProvider.trimSmithing(recipeOutput, ingredientItem, location)
		}

		/** Generated override to expose protected method: [RecipeProvider.twoByTwoPacker]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun twoByTwoPacker(recipeOutput: RecipeOutput, category: RecipeCategory, packed: ItemLike, unpacked: ItemLike) {
			RecipeProvider.twoByTwoPacker(recipeOutput, category, packed, unpacked)
		}

		/** Generated override to expose protected method: [RecipeProvider.threeByThreePacker]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun threeByThreePacker(recipeOutput: RecipeOutput, category: RecipeCategory, packed: ItemLike, unpacked: ItemLike, criterionName: String) {
			RecipeProvider.threeByThreePacker(recipeOutput, category, packed, unpacked, criterionName)
		}

		/** Generated override to expose protected method: [RecipeProvider.threeByThreePacker]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun threeByThreePacker(recipeOutput: RecipeOutput, category: RecipeCategory, packed: ItemLike, unpacked: ItemLike) {
			RecipeProvider.threeByThreePacker(recipeOutput, category, packed, unpacked)
		}

		/** Generated override to expose protected method: [RecipeProvider.planksFromLog]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun planksFromLog(recipeOutput: RecipeOutput, planks: ItemLike, logs: TagKey<Item>, resultCount: Int) {
			RecipeProvider.planksFromLog(recipeOutput, planks, logs, resultCount)
		}

		/** Generated override to expose protected method: [RecipeProvider.planksFromLogs]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun planksFromLogs(recipeOutput: RecipeOutput, planks: ItemLike, logs: TagKey<Item>, result: Int) {
			RecipeProvider.planksFromLogs(recipeOutput, planks, logs, result)
		}

		/** Generated override to expose protected method: [RecipeProvider.woodFromLogs]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun woodFromLogs(recipeOutput: RecipeOutput, wood: ItemLike, log: ItemLike) {
			RecipeProvider.woodFromLogs(recipeOutput, wood, log)
		}

		/** Generated override to expose protected method: [RecipeProvider.woodenBoat]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun woodenBoat(recipeOutput: RecipeOutput, boat: ItemLike, material: ItemLike) {
			RecipeProvider.woodenBoat(recipeOutput, boat, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.chestBoat]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun chestBoat(recipeOutput: RecipeOutput, boat: ItemLike, material: ItemLike) {
			RecipeProvider.chestBoat(recipeOutput, boat, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.buttonBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun buttonBuilder(button: ItemLike, material: Ingredient): RecipeBuilder = RecipeProvider.buttonBuilder(button, material)

		/** Generated override to expose protected method: [RecipeProvider.doorBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun doorBuilder(door: ItemLike, material: Ingredient): RecipeBuilder = RecipeProvider.doorBuilder(door, material)

		/** Generated override to expose protected method: [RecipeProvider.fenceBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun fenceBuilder(fence: ItemLike, material: Ingredient): RecipeBuilder = RecipeProvider.fenceBuilder(fence, material)

		/** Generated override to expose protected method: [RecipeProvider.fenceGateBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun fenceGateBuilder(fenceGate: ItemLike, material: Ingredient): RecipeBuilder = RecipeProvider.fenceGateBuilder(fenceGate, material)

		/** Generated override to expose protected method: [RecipeProvider.pressurePlate]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun pressurePlate(recipeOutput: RecipeOutput, pressurePlate: ItemLike, material: ItemLike) {
			RecipeProvider.pressurePlate(recipeOutput, pressurePlate, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.pressurePlateBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun pressurePlateBuilder(category: RecipeCategory, pressurePlate: ItemLike, material: Ingredient): RecipeBuilder = RecipeProvider.pressurePlateBuilder(category, pressurePlate, material)

		/** Generated override to expose protected method: [RecipeProvider.slab]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun slab(recipeOutput: RecipeOutput, category: RecipeCategory, slab: ItemLike, material: ItemLike) {
			RecipeProvider.slab(recipeOutput, category, slab, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.slabBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun slabBuilder(category: RecipeCategory, slab: ItemLike, material: Ingredient): RecipeBuilder = RecipeProvider.slabBuilder(category, slab, material)

		/** Generated override to expose protected method: [RecipeProvider.stairBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun stairBuilder(stairs: ItemLike, material: Ingredient): RecipeBuilder = RecipeProvider.stairBuilder(stairs, material)

		/** Generated override to expose protected method: [RecipeProvider.trapdoorBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun trapdoorBuilder(trapdoor: ItemLike, material: Ingredient): RecipeBuilder = RecipeProvider.trapdoorBuilder(trapdoor, material)

		/** Generated override to expose protected method: [RecipeProvider.signBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun signBuilder(sign: ItemLike, material: Ingredient): RecipeBuilder = RecipeProvider.signBuilder(sign, material)

		/** Generated override to expose protected method: [RecipeProvider.hangingSign]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun hangingSign(recipeOutput: RecipeOutput, sign: ItemLike, material: ItemLike) {
			RecipeProvider.hangingSign(recipeOutput, sign, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.colorBlockWithDye]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun colorBlockWithDye(recipeOutput: RecipeOutput, dyes: MutableList<Item>, dyeableItems: MutableList<Item>, group: String) {
			RecipeProvider.colorBlockWithDye(recipeOutput, dyes, dyeableItems, group)
		}

		/** Generated override to expose protected method: [RecipeProvider.carpet]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun carpet(recipeOutput: RecipeOutput, carpet: ItemLike, material: ItemLike) {
			RecipeProvider.carpet(recipeOutput, carpet, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.bedFromPlanksAndWool]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun bedFromPlanksAndWool(recipeOutput: RecipeOutput, bed: ItemLike, wool: ItemLike) {
			RecipeProvider.bedFromPlanksAndWool(recipeOutput, bed, wool)
		}

		/** Generated override to expose protected method: [RecipeProvider.banner]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun banner(recipeOutput: RecipeOutput, banner: ItemLike, material: ItemLike) {
			RecipeProvider.banner(recipeOutput, banner, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.stainedGlassFromGlassAndDye]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun stainedGlassFromGlassAndDye(recipeOutput: RecipeOutput, stainedGlass: ItemLike, dye: ItemLike) {
			RecipeProvider.stainedGlassFromGlassAndDye(recipeOutput, stainedGlass, dye)
		}

		/** Generated override to expose protected method: [RecipeProvider.stainedGlassPaneFromStainedGlass]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun stainedGlassPaneFromStainedGlass(recipeOutput: RecipeOutput, stainedGlassPane: ItemLike, stainedGlass: ItemLike) {
			RecipeProvider.stainedGlassPaneFromStainedGlass(recipeOutput, stainedGlassPane, stainedGlass)
		}

		/** Generated override to expose protected method: [RecipeProvider.stainedGlassPaneFromGlassPaneAndDye]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun stainedGlassPaneFromGlassPaneAndDye(recipeOutput: RecipeOutput, stainedGlassPane: ItemLike, dye: ItemLike) {
			RecipeProvider.stainedGlassPaneFromGlassPaneAndDye(recipeOutput, stainedGlassPane, dye)
		}

		/** Generated override to expose protected method: [RecipeProvider.coloredTerracottaFromTerracottaAndDye]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun coloredTerracottaFromTerracottaAndDye(recipeOutput: RecipeOutput, terracotta: ItemLike, dye: ItemLike) {
			RecipeProvider.coloredTerracottaFromTerracottaAndDye(recipeOutput, terracotta, dye)
		}

		/** Generated override to expose protected method: [RecipeProvider.concretePowder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun concretePowder(recipeOutput: RecipeOutput, concretePowder: ItemLike, dye: ItemLike) {
			RecipeProvider.concretePowder(recipeOutput, concretePowder, dye)
		}

		/** Generated override to expose protected method: [RecipeProvider.candle]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun candle(recipeOutput: RecipeOutput, candle: ItemLike, dye: ItemLike) {
			RecipeProvider.candle(recipeOutput, candle, dye)
		}

		/** Generated override to expose protected method: [RecipeProvider.wall]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun wall(recipeOutput: RecipeOutput, category: RecipeCategory, wall: ItemLike, material: ItemLike) {
			RecipeProvider.wall(recipeOutput, category, wall, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.wallBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun wallBuilder(category: RecipeCategory, wall: ItemLike, material: Ingredient): RecipeBuilder = RecipeProvider.wallBuilder(category, wall, material)

		/** Generated override to expose protected method: [RecipeProvider.polished]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun polished(recipeOutput: RecipeOutput, category: RecipeCategory, result: ItemLike, material: ItemLike) {
			RecipeProvider.polished(recipeOutput, category, result, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.polishedBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun polishedBuilder(category: RecipeCategory, result: ItemLike, material: Ingredient): RecipeBuilder = RecipeProvider.polishedBuilder(category, result, material)

		/** Generated override to expose protected method: [RecipeProvider.cut]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun cut(recipeOutput: RecipeOutput, category: RecipeCategory, cutResult: ItemLike, material: ItemLike) {
			RecipeProvider.cut(recipeOutput, category, cutResult, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.cutBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun cutBuilder(category: RecipeCategory, cutResult: ItemLike, material: Ingredient): ShapedRecipeBuilder = RecipeProvider.cutBuilder(category, cutResult, material)

		/** Generated override to expose protected method: [RecipeProvider.chiseled]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun chiseled(recipeOutput: RecipeOutput, category: RecipeCategory, chiseledResult: ItemLike, material: ItemLike) {
			RecipeProvider.chiseled(recipeOutput, category, chiseledResult, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.mosaicBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun mosaicBuilder(recipeOutput: RecipeOutput, category: RecipeCategory, result: ItemLike, material: ItemLike) {
			RecipeProvider.mosaicBuilder(recipeOutput, category, result, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.chiseledBuilder]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun chiseledBuilder(category: RecipeCategory, chiseledResult: ItemLike, material: Ingredient): ShapedRecipeBuilder = RecipeProvider.chiseledBuilder(category, chiseledResult, material)

		/** Generated override to expose protected method: [RecipeProvider.stonecutterResultFromBase]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun stonecutterResultFromBase(recipeOutput: RecipeOutput, category: RecipeCategory, result: ItemLike, material: ItemLike) {
			RecipeProvider.stonecutterResultFromBase(recipeOutput, category, result, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.stonecutterResultFromBase]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun stonecutterResultFromBase(recipeOutput: RecipeOutput, category: RecipeCategory, result: ItemLike, material: ItemLike, resultCount: Int) {
			RecipeProvider.stonecutterResultFromBase(recipeOutput, category, result, material, resultCount)
		}

		/** Generated override to expose protected method: [RecipeProvider.smeltingResultFromBase]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun smeltingResultFromBase(recipeOutput: RecipeOutput, result: ItemLike, ingredient: ItemLike) {
			RecipeProvider.smeltingResultFromBase(recipeOutput, result, ingredient)
		}

		/** Generated override to expose protected method: [RecipeProvider.copySmithingTemplate]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun copySmithingTemplate(recipeOutput: RecipeOutput, template: ItemLike, baseMaterial: TagKey<Item>) {
			RecipeProvider.copySmithingTemplate(recipeOutput, template, baseMaterial)
		}

		/** Generated override to expose protected method: [RecipeProvider.copySmithingTemplate]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun copySmithingTemplate(recipeOutput: RecipeOutput, template: ItemLike, baseItem: ItemLike) {
			RecipeProvider.copySmithingTemplate(recipeOutput, template, baseItem)
		}

		/** Generated override to expose protected method: [RecipeProvider.copySmithingTemplate]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun copySmithingTemplate(recipeOutput: RecipeOutput, template: ItemLike, baseItem: Ingredient) {
			RecipeProvider.copySmithingTemplate(recipeOutput, template, baseItem)
		}

		/** Generated override to expose protected method: [RecipeProvider.waxRecipes]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun waxRecipes(recipeOutput: RecipeOutput, requiredFeatures: FeatureFlagSet) {
			RecipeProvider.waxRecipes(recipeOutput, requiredFeatures)
		}

		/** Generated override to expose protected method: [RecipeProvider.grate]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun grate(recipeOutput: RecipeOutput, grateBlock: Block, material: Block) {
			RecipeProvider.grate(recipeOutput, grateBlock, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.copperBulb]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun copperBulb(recipeOutput: RecipeOutput, bulbBlock: Block, material: Block) {
			RecipeProvider.copperBulb(recipeOutput, bulbBlock, material)
		}

		/** Generated override to expose protected method: [RecipeProvider.generateRecipes]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun generateRecipes(recipeOutput: RecipeOutput, blockFamily: BlockFamily, requiredFeatures: FeatureFlagSet) {
			RecipeProvider.generateRecipes(recipeOutput, blockFamily, requiredFeatures)
		}

		/** Generated override to expose protected method: [RecipeProvider.getBaseBlock]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun getBaseBlock(family: BlockFamily, variant: BlockFamily.Variant): Block = RecipeProvider.getBaseBlock(family, variant)

		/** Generated override to expose protected method: [RecipeProvider.insideOf]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun insideOf(block: Block): Criterion<EnterBlockTrigger.TriggerInstance> = RecipeProvider.insideOf(block)

		/** Generated override to expose protected method: [RecipeProvider.has]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun has(count: MinMaxBounds.Ints, item: ItemLike): Criterion<InventoryChangeTrigger.TriggerInstance> = RecipeProvider.has(count, item)

		/** Generated override to expose protected method: [RecipeProvider.has]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun has(itemLike: ItemLike): Criterion<InventoryChangeTrigger.TriggerInstance> = RecipeProvider.has(itemLike)

		/** Generated override to expose protected method: [RecipeProvider.has]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun has(tag: TagKey<Item>): Criterion<InventoryChangeTrigger.TriggerInstance> = RecipeProvider.has(tag)

		/** Generated override to expose protected method: [RecipeProvider.inventoryTrigger]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun inventoryTrigger(vararg items: ItemPredicate.Builder): Criterion<InventoryChangeTrigger.TriggerInstance> = RecipeProvider.inventoryTrigger(*items)

		/** Generated override to expose protected method: [RecipeProvider.inventoryTrigger]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun inventoryTrigger(vararg predicates: ItemPredicate): Criterion<InventoryChangeTrigger.TriggerInstance> = RecipeProvider.inventoryTrigger(*predicates)

		/** Generated override to expose protected method: [RecipeProvider.getHasName]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun getHasName(itemLike: ItemLike): String = RecipeProvider.getHasName(itemLike)

		/** Generated override to expose protected method: [RecipeProvider.getItemName]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun getItemName(itemLike: ItemLike): String = RecipeProvider.getItemName(itemLike)

		/** Generated override to expose protected method: [RecipeProvider.getSimpleRecipeName]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun getSimpleRecipeName(itemLike: ItemLike): String = RecipeProvider.getSimpleRecipeName(itemLike)

		/** Generated override to expose protected method: [RecipeProvider.getConversionRecipeName]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun getConversionRecipeName(result: ItemLike, ingredient: ItemLike): String = RecipeProvider.getConversionRecipeName(result, ingredient)

		/** Generated override to expose protected method: [RecipeProvider.getSmeltingRecipeName]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun getSmeltingRecipeName(itemLike: ItemLike): String = RecipeProvider.getSmeltingRecipeName(itemLike)

		/** Generated override to expose protected method: [RecipeProvider.getBlastingRecipeName]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Tue, 28 Apr 2026 01:45:28 GMT")
		@JvmStatic
		fun getBlastingRecipeName(itemLike: ItemLike): String = RecipeProvider.getBlastingRecipeName(itemLike)
		// GENERATED STATIC END
	}
}
