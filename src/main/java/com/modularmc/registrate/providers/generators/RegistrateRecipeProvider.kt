// Expose the inherited protected Java static helpers to Java callers.
@file:Suppress("ACCIDENTAL_OVERRIDE")

package com.modularmc.registrate.providers.generators

import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.util.DataIngredient
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.advancements.Advancement
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.advancements.Criterion
import net.minecraft.advancements.criterion.EnterBlockTrigger
import net.minecraft.advancements.criterion.InventoryChangeTrigger
import net.minecraft.advancements.criterion.ItemPredicate
import net.minecraft.advancements.criterion.MinMaxBounds
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.BlockFamily
import net.minecraft.data.recipes.RecipeBuilder
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.data.recipes.ShapedRecipeBuilder
import net.minecraft.data.recipes.ShapelessRecipeBuilder
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder
import net.minecraft.data.recipes.SingleItemRecipeBuilder
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.AbstractCookingRecipe
import net.minecraft.world.item.crafting.BlastingRecipe
import net.minecraft.world.item.crafting.CampfireCookingRecipe
import net.minecraft.world.item.crafting.CookingBookCategory
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.SmeltingRecipe
import net.minecraft.world.item.crafting.SmokingRecipe
import net.minecraft.world.item.equipment.trim.TrimPattern
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SuspiciousEffectHolder
import net.neoforged.neoforge.common.Tags
import net.neoforged.neoforge.common.conditions.ICondition

import java.util.function.Supplier
import javax.annotation.CheckReturnValue
import javax.annotation.processing.Generated

open class RegistrateRecipeProvider(private val runner: RegistrateRecipeRunner, registries: HolderLookup.Provider, output: RecipeOutput) :
	RecipeProvider(registries, output),
	RecipeOutput {
	private val itemLookup = registries.lookupOrThrow(Registries.ITEM)
	private val blockLookup = registries.lookupOrThrow(Registries.BLOCK)
	private val entityLookup = registries.lookupOrThrow(Registries.ENTITY_TYPE)

	override fun accept(id: ResourceKey<Recipe<*>>, recipe: Recipe<*>, advancement: AdvancementHolder?) = output.accept(id, recipe, advancement)

	override fun accept(id: ResourceKey<Recipe<*>>, recipe: Recipe<*>, advancement: AdvancementHolder?, vararg conditions: ICondition) = output.accept(id, recipe, advancement, *conditions)

	override fun advancement(): Advancement.Builder = output.advancement()

	override fun includeRootAdvancement() = output.includeRootAdvancement()

	public override fun buildRecipes() {
		runner.provider = this
		try {
			runner.owner.genData(ProviderType.RECIPE, this)
		} finally {
			runner.provider = null
		}
	}

	fun registries(): HolderLookup.Provider = registries

	fun itemLookup(): HolderLookup<Item> = itemLookup

	fun blockLookup(): HolderLookup<Block> = blockLookup

	fun entityLookup(): HolderLookup<EntityType<*>> = entityLookup

	fun <T : Any> resolve(key: ResourceKey<T>): Holder<T> = registries.lookupOrThrow(key.registryKey()).getOrThrow(key)

	fun safeId(id: Identifier): Identifier = Identifier.fromNamespaceAndPath(runner.owner.modid, safeName(id))

	fun safeId(source: DataIngredient): Identifier = safeId(source.id)

	fun safeId(registryEntry: ItemLike): Identifier = safeId(BuiltInRegistries.ITEM.getKey(registryEntry.asItem()))

	fun safeKey(id: Identifier): ResourceKey<Recipe<*>> = ResourceKey.create(
		Registries.RECIPE,
		Identifier.fromNamespaceAndPath(runner.owner.modid, safeName(id)),
	)

	fun safeKey(source: DataIngredient): ResourceKey<Recipe<*>> = safeKey(source.id)

	fun safeKey(registryEntry: ItemLike): ResourceKey<Recipe<*>> = safeKey(BuiltInRegistries.ITEM.getKey(registryEntry.asItem()))

	fun safeName(id: Identifier): String = id.path.replace('/', '_')

	fun safeName(source: DataIngredient): String = safeName(source.id)

	fun safeName(registryEntry: ItemLike): String = safeName(BuiltInRegistries.ITEM.getKey(registryEntry.asItem()))

	fun <T : ItemLike, S : AbstractCookingRecipe> cooking(
		source: DataIngredient,
		craftingCategory: RecipeCategory,
		cookingCategory: CookingBookCategory,
		result: Supplier<out T>,
		experience: Float,
		cookingTime: Int,
		typeName: String,
		factory: AbstractCookingRecipe.Factory<S>,
	) {
		SimpleCookingRecipeBuilder.generic(
			source.toVanilla(),
			craftingCategory,
			cookingCategory,
			result.get(),
			experience,
			cookingTime,
			factory,
		)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeId(result.get()).toString() + "_from_" + safeName(source) + "_" + typeName)
	}

	fun <T : ItemLike> smelting(source: DataIngredient, category: RecipeCategory, cookingCategory: CookingBookCategory, result: Supplier<out T>, experience: Float) {
		smelting(
			source,
			category,
			cookingCategory,
			result,
			experience,
			DEFAULT_SMELT_TIME,
		)
	}

	fun <T : ItemLike> smelting(source: DataIngredient, category: RecipeCategory, cookingCategory: CookingBookCategory, result: Supplier<out T>, experience: Float, cookingTime: Int) {
		cooking(
			source,
			category,
			cookingCategory,
			result,
			experience,
			cookingTime,
			"smelting",
			::SmeltingRecipe,
		)
	}

	fun <T : ItemLike> blasting(source: DataIngredient, category: RecipeCategory, cookingCategory: CookingBookCategory, result: Supplier<out T>, experience: Float) {
		blasting(
			source,
			category,
			cookingCategory,
			result,
			experience,
			DEFAULT_BLAST_TIME,
		)
	}

	fun <T : ItemLike> blasting(source: DataIngredient, category: RecipeCategory, cookingCategory: CookingBookCategory, result: Supplier<out T>, experience: Float, cookingTime: Int) {
		cooking(
			source,
			category,
			cookingCategory,
			result,
			experience,
			cookingTime,
			"blasting",
			::BlastingRecipe,
		)
	}

	fun <T : ItemLike> smoking(source: DataIngredient, category: RecipeCategory, cookingCategory: CookingBookCategory, result: Supplier<out T>, experience: Float) {
		smoking(
			source,
			category,
			cookingCategory,
			result,
			experience,
			DEFAULT_SMOKE_TIME,
		)
	}

	fun <T : ItemLike> smoking(source: DataIngredient, category: RecipeCategory, cookingCategory: CookingBookCategory, result: Supplier<out T>, experience: Float, cookingTime: Int) {
		cooking(
			source,
			category,
			cookingCategory,
			result,
			experience,
			cookingTime,
			"smoking",
			::SmokingRecipe,
		)
	}

	fun <T : ItemLike> campfire(source: DataIngredient, category: RecipeCategory, cookingCategory: CookingBookCategory, result: Supplier<out T>, experience: Float) {
		campfire(
			source,
			category,
			cookingCategory,
			result,
			experience,
			DEFAULT_CAMPFIRE_TIME,
		)
	}

	fun <T : ItemLike> campfire(source: DataIngredient, category: RecipeCategory, cookingCategory: CookingBookCategory, result: Supplier<out T>, experience: Float, cookingTime: Int) {
		cooking(
			source,
			category,
			cookingCategory,
			result,
			experience,
			cookingTime,
			"campfire",
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

	fun <T : ItemLike> smeltingAndBlasting(source: DataIngredient, category: RecipeCategory, cookingCategory: CookingBookCategory, result: Supplier<out T>, xp: Float) {
		smelting(source, category, cookingCategory, result, xp)
		blasting(source, category, cookingCategory, result, xp)
	}

	fun <T : ItemLike> food(source: DataIngredient, category: RecipeCategory, cookingCategory: CookingBookCategory, result: Supplier<out T>, xp: Float) {
		smelting(source, category, cookingCategory, result, xp)
		smoking(source, category, cookingCategory, result, xp)
		campfire(source, category, cookingCategory, result, xp)
	}

	fun <T : ItemLike> square(source: DataIngredient, category: RecipeCategory, output: Supplier<out T>, small: Boolean) {
		val builder: ShapedRecipeBuilder = shaped(category, output.get())
			.define('X', source.toVanilla())
		if (small) {
			builder.pattern("XX").pattern("XX")
		} else {
			builder.pattern("XXX").pattern("XXX").pattern("XXX")
		}
		builder.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeKey(output.get()))
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
		shapeless(category, result.get(), amount)
			.requires(source.toVanilla(), required)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))

	fun <T : ItemLike> singleItem(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, required: Int, amount: Int) {
		singleItemUnfinished(source, category, result, required, amount).save(this, safeKey(result.get()))
	}

	fun <T : ItemLike> planks(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>) {
		singleItemUnfinished(source, category, result, 1, 4)
			.group("planks")
			.save(this, safeKey(result.get()))
	}

	fun <T : ItemLike> stairs(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, group: String?, stone: Boolean) {
		shaped(category, result.get(), 4)
			.pattern("X  ").pattern("XX ").pattern("XXX")
			.define('X', source.toVanilla())
			.group(group)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeKey(result.get()))
		if (stone) {
			stonecutting(source, category, result)
		}
	}

	fun <T : ItemLike> slab(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, group: String?, stone: Boolean) {
		shaped(category, result.get(), 6)
			.pattern("XXX")
			.define('X', source.toVanilla())
			.group(group)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeKey(result.get()))
		if (stone) {
			stonecutting(source, category, result, 2)
		}
	}

	fun <T : ItemLike> fence(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, group: String?) {
		shaped(category, result.get(), 3)
			.pattern("W#W").pattern("W#W")
			.define('W', source.toVanilla())
			.define('#', Tags.Items.RODS_WOODEN)
			.group(group)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeKey(result.get()))
	}

	fun <T : ItemLike> fenceGate(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, group: String?) {
		shaped(category, result.get())
			.pattern("#W#").pattern("#W#")
			.define('W', source.toVanilla())
			.define('#', Tags.Items.RODS_WOODEN)
			.group(group)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeKey(result.get()))
	}

	fun <T : ItemLike> wall(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>) {
		shaped(category, result.get(), 6)
			.pattern("XXX").pattern("XXX")
			.define('X', source.toVanilla())
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeKey(result.get()))
		stonecutting(source, category, result)
	}

	fun <T : ItemLike> door(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, group: String?) {
		shaped(category, result.get(), 3)
			.pattern("XX").pattern("XX").pattern("XX")
			.define('X', source.toVanilla())
			.group(group)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeKey(result.get()))
	}

	fun <T : ItemLike> trapDoor(source: DataIngredient, category: RecipeCategory, result: Supplier<out T>, group: String?) {
		shaped(category, result.get(), 2)
			.pattern("XXX").pattern("XXX")
			.define('X', source.toVanilla())
			.group(group)
			.unlockedBy("has_" + safeName(source), source.getCriterion(this))
			.save(this, safeKey(result.get()))
	}

	// @formatter:off
	// GENERATED START - DO NOT EDIT BELOW THIS LINE

	/** Generated override to expose protected method: [RecipeProvider.generateForEnabledBlockFamilies]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun generateForEnabledBlockFamilies(flagSet: FeatureFlagSet) {
		super.generateForEnabledBlockFamilies(flagSet)
	}

	/** Generated override to expose protected method: [RecipeProvider.netheriteSmithing]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun netheriteSmithing(base: Item, category: RecipeCategory, result: Item) {
		super.netheriteSmithing(base, category, result)
	}

	/** Generated override to expose protected method: [RecipeProvider.trimSmithing]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun trimSmithing(trimTemplate: Item, patternId: ResourceKey<TrimPattern>, id: ResourceKey<Recipe<*>>) {
		super.trimSmithing(trimTemplate, patternId, id)
	}

	/** Generated override to expose protected method: [RecipeProvider.twoByTwoPacker]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun twoByTwoPacker(category: RecipeCategory, result: ItemLike, ingredient: ItemLike) {
		super.twoByTwoPacker(category, result, ingredient)
	}

	/** Generated override to expose protected method: [RecipeProvider.threeByThreePacker]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun threeByThreePacker(category: RecipeCategory, result: ItemLike, ingredient: ItemLike, unlockedBy: String) {
		super.threeByThreePacker(category, result, ingredient, unlockedBy)
	}

	/** Generated override to expose protected method: [RecipeProvider.threeByThreePacker]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun threeByThreePacker(category: RecipeCategory, result: ItemLike, ingredient: ItemLike) {
		super.threeByThreePacker(category, result, ingredient)
	}

	/** Generated override to expose protected method: [RecipeProvider.planksFromLog]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun planksFromLog(result: ItemLike, logs: TagKey<Item>, count: Int) {
		super.planksFromLog(result, logs, count)
	}

	/** Generated override to expose protected method: [RecipeProvider.planksFromLogs]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun planksFromLogs(result: ItemLike, logs: TagKey<Item>, count: Int) {
		super.planksFromLogs(result, logs, count)
	}

	/** Generated override to expose protected method: [RecipeProvider.woodFromLogs]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun woodFromLogs(result: ItemLike, log: ItemLike) {
		super.woodFromLogs(result, log)
	}

	/** Generated override to expose protected method: [RecipeProvider.woodenBoat]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun woodenBoat(result: ItemLike, planks: ItemLike) {
		super.woodenBoat(result, planks)
	}

	/** Generated override to expose protected method: [RecipeProvider.chestBoat]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun chestBoat(chestBoat: ItemLike, boat: ItemLike) {
		super.chestBoat(chestBoat, boat)
	}

	/** Generated override to expose protected method: [RecipeProvider.buttonBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun buttonBuilder(result: ItemLike, base: Ingredient): RecipeBuilder = super.buttonBuilder(result, base)

	/** Generated override to expose protected method: [RecipeProvider.doorBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun doorBuilder(result: ItemLike, base: Ingredient): RecipeBuilder = super.doorBuilder(result, base)

	/** Generated override to expose protected method: [RecipeProvider.fenceBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun fenceBuilder(result: ItemLike, base: Ingredient): RecipeBuilder = super.fenceBuilder(result, base)

	/** Generated override to expose protected method: [RecipeProvider.fenceGateBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun fenceGateBuilder(result: ItemLike, planks: Ingredient): RecipeBuilder = super.fenceGateBuilder(result, planks)

	/** Generated override to expose protected method: [RecipeProvider.pressurePlate]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun pressurePlate(result: ItemLike, base: ItemLike) {
		super.pressurePlate(result, base)
	}

	/** Generated override to expose protected method: [RecipeProvider.pressurePlateBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun pressurePlateBuilder(category: RecipeCategory, result: ItemLike, base: Ingredient): RecipeBuilder = super.pressurePlateBuilder(category, result, base)

	/** Generated override to expose protected method: [RecipeProvider.slab]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun slab(category: RecipeCategory, result: ItemLike, base: ItemLike) {
		super.slab(category, result, base)
	}

	/** Generated override to expose protected method: [RecipeProvider.shelf]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun shelf(result: ItemLike, strippedLogs: ItemLike) {
		super.shelf(result, strippedLogs)
	}

	/** Generated override to expose protected method: [RecipeProvider.slabBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun slabBuilder(category: RecipeCategory, result: ItemLike, base: Ingredient): RecipeBuilder = super.slabBuilder(category, result, base)

	/** Generated override to expose protected method: [RecipeProvider.stairBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun stairBuilder(result: ItemLike, base: Ingredient): RecipeBuilder = super.stairBuilder(result, base)

	/** Generated override to expose protected method: [RecipeProvider.trapdoorBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun trapdoorBuilder(result: ItemLike, base: Ingredient): RecipeBuilder = super.trapdoorBuilder(result, base)

	/** Generated override to expose protected method: [RecipeProvider.signBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun signBuilder(result: ItemLike, planks: Ingredient): RecipeBuilder = super.signBuilder(result, planks)

	/** Generated override to expose protected method: [RecipeProvider.hangingSign]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun hangingSign(result: ItemLike, ingredient: ItemLike) {
		super.hangingSign(result, ingredient)
	}

	/** Generated override to expose protected method: [RecipeProvider.colorItemWithDye]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun colorItemWithDye(dyes: List<Item>, items: List<Item>, groupName: String, category: RecipeCategory) {
		super.colorItemWithDye(dyes, items, groupName, category)
	}

	/** Generated override to expose protected method: [RecipeProvider.carpet]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun carpet(result: ItemLike, sourceItem: ItemLike) {
		super.carpet(result, sourceItem)
	}

	/** Generated override to expose protected method: [RecipeProvider.bedFromPlanksAndWool]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun bedFromPlanksAndWool(result: ItemLike, wool: ItemLike) {
		super.bedFromPlanksAndWool(result, wool)
	}

	/** Generated override to expose protected method: [RecipeProvider.banner]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun banner(result: ItemLike, wool: ItemLike) {
		super.banner(result, wool)
	}

	/** Generated override to expose protected method: [RecipeProvider.stainedGlassFromGlassAndDye]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun stainedGlassFromGlassAndDye(result: ItemLike, dye: ItemLike) {
		super.stainedGlassFromGlassAndDye(result, dye)
	}

	/** Generated override to expose protected method: [RecipeProvider.dryGhast]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun dryGhast(result: ItemLike) {
		super.dryGhast(result)
	}

	/** Generated override to expose protected method: [RecipeProvider.harness]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun harness(result: ItemLike, wool: ItemLike) {
		super.harness(result, wool)
	}

	/** Generated override to expose protected method: [RecipeProvider.stainedGlassPaneFromStainedGlass]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun stainedGlassPaneFromStainedGlass(result: ItemLike, stainedGlass: ItemLike) {
		super.stainedGlassPaneFromStainedGlass(result, stainedGlass)
	}

	/** Generated override to expose protected method: [RecipeProvider.stainedGlassPaneFromGlassPaneAndDye]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun stainedGlassPaneFromGlassPaneAndDye(result: ItemLike, dye: ItemLike) {
		super.stainedGlassPaneFromGlassPaneAndDye(result, dye)
	}

	/** Generated override to expose protected method: [RecipeProvider.coloredTerracottaFromTerracottaAndDye]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun coloredTerracottaFromTerracottaAndDye(result: ItemLike, dye: ItemLike) {
		super.coloredTerracottaFromTerracottaAndDye(result, dye)
	}

	/** Generated override to expose protected method: [RecipeProvider.concretePowder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun concretePowder(result: ItemLike, dye: ItemLike) {
		super.concretePowder(result, dye)
	}

	/** Generated override to expose protected method: [RecipeProvider.candle]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun candle(result: ItemLike, dye: ItemLike) {
		super.candle(result, dye)
	}

	/** Generated override to expose protected method: [RecipeProvider.wall]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun wall(category: RecipeCategory, result: ItemLike, base: ItemLike) {
		super.wall(category, result, base)
	}

	/** Generated override to expose protected method: [RecipeProvider.wallBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun wallBuilder(category: RecipeCategory, result: ItemLike, base: Ingredient): RecipeBuilder = super.wallBuilder(category, result, base)

	/** Generated override to expose protected method: [RecipeProvider.polished]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun polished(category: RecipeCategory, result: ItemLike, base: ItemLike) {
		super.polished(category, result, base)
	}

	/** Generated override to expose protected method: [RecipeProvider.polishedBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun polishedBuilder(category: RecipeCategory, result: ItemLike, base: Ingredient): RecipeBuilder = super.polishedBuilder(category, result, base)

	/** Generated override to expose protected method: [RecipeProvider.cut]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun cut(category: RecipeCategory, result: ItemLike, base: ItemLike) {
		super.cut(category, result, base)
	}

	/** Generated override to expose protected method: [RecipeProvider.cutBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun cutBuilder(category: RecipeCategory, result: ItemLike, base: Ingredient): ShapedRecipeBuilder = super.cutBuilder(category, result, base)

	/** Generated override to expose protected method: [RecipeProvider.chiseled]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun chiseled(category: RecipeCategory, result: ItemLike, base: ItemLike) {
		super.chiseled(category, result, base)
	}

	/** Generated override to expose protected method: [RecipeProvider.mosaicBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun mosaicBuilder(category: RecipeCategory, result: ItemLike, base: ItemLike) {
		super.mosaicBuilder(category, result, base)
	}

	/** Generated override to expose protected method: [RecipeProvider.chiseledBuilder]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun chiseledBuilder(category: RecipeCategory, result: ItemLike, base: Ingredient): ShapedRecipeBuilder = super.chiseledBuilder(category, result, base)

	/** Generated override to expose protected method: [RecipeProvider.stonecutterResultFromBase]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun stonecutterResultFromBase(category: RecipeCategory, result: ItemLike, base: ItemLike) {
		super.stonecutterResultFromBase(category, result, base)
	}

	/** Generated override to expose protected method: [RecipeProvider.stonecutterResultFromBase]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun stonecutterResultFromBase(category: RecipeCategory, result: ItemLike, base: ItemLike, count: Int) {
		super.stonecutterResultFromBase(category, result, base, count)
	}

	/** Generated override to expose protected method: [RecipeProvider.smeltingResultFromBase]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun smeltingResultFromBase(result: ItemLike, base: ItemLike) {
		super.smeltingResultFromBase(result, base)
	}

	/** Generated override to expose protected method: [RecipeProvider.nineBlockStorageRecipes]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun nineBlockStorageRecipes(unpackedFormCategory: RecipeCategory, unpackedForm: ItemLike, packedFormCategory: RecipeCategory, packedForm: ItemLike) {
		super.nineBlockStorageRecipes(unpackedFormCategory, unpackedForm, packedFormCategory, packedForm)
	}

	/** Generated override to expose protected method: [RecipeProvider.copySmithingTemplate]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun copySmithingTemplate(smithingTemplate: ItemLike, baseMaterial: ItemLike) {
		super.copySmithingTemplate(smithingTemplate, baseMaterial)
	}

	/** Generated override to expose protected method: [RecipeProvider.copySmithingTemplate]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun copySmithingTemplate(smithingTemplate: ItemLike, baseMaterials: Ingredient) {
		super.copySmithingTemplate(smithingTemplate, baseMaterials)
	}

	/** Generated override to expose protected method: [RecipeProvider.cookRecipes]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun <T : AbstractCookingRecipe> cookRecipes(source: String, factory: AbstractCookingRecipe.Factory<T>, cookingTime: Int) {
		super.cookRecipes(source, factory, cookingTime)
	}

	/** Generated override to expose protected method: [RecipeProvider.waxRecipes]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun waxRecipes(flagSet: FeatureFlagSet) {
		super.waxRecipes(flagSet)
	}

	/** Generated override to expose protected method: [RecipeProvider.grate]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun grate(grateBlock: Block, material: Block) {
		super.grate(grateBlock, material)
	}

	/** Generated override to expose protected method: [RecipeProvider.copperBulb]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun copperBulb(copperBulb: Block, copperMaterial: Block) {
		super.copperBulb(copperBulb, copperMaterial)
	}

	/** Generated override to expose protected method: [RecipeProvider.waxedChiseled]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun waxedChiseled(result: Block, material: Block) {
		super.waxedChiseled(result, material)
	}

	/** Generated override to expose protected method: [RecipeProvider.suspiciousStew]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun suspiciousStew(item: Item, effectHolder: SuspiciousEffectHolder) {
		super.suspiciousStew(item, effectHolder)
	}

	/** Generated override to expose protected method: [RecipeProvider.dyedItem]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun dyedItem(target: Item, group: String) {
		super.dyedItem(target, group)
	}

	/** Generated override to expose protected method: [RecipeProvider.dyedShulkerBoxRecipe]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun dyedShulkerBoxRecipe(dye: Item, dyedResult: Item) {
		super.dyedShulkerBoxRecipe(dye, dyedResult)
	}

	/** Generated override to expose protected method: [RecipeProvider.dyedBundleRecipe]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun dyedBundleRecipe(dye: Item, dyedResult: Item) {
		super.dyedBundleRecipe(dye, dyedResult)
	}

	/** Generated override to expose protected method: [RecipeProvider.generateRecipes]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun generateRecipes(family: BlockFamily, flagSet: FeatureFlagSet) {
		super.generateRecipes(family, flagSet)
	}

	/** Generated override to expose protected method: [RecipeProvider.getBaseBlockForCrafting]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun getBaseBlockForCrafting(family: BlockFamily, variant: BlockFamily.Variant): Block = super.getBaseBlockForCrafting(family, variant)

	/** Generated override to expose protected method: [RecipeProvider.has]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun has(count: MinMaxBounds.Ints, item: ItemLike): Criterion<InventoryChangeTrigger.TriggerInstance> = super.has(count, item)

	/** Generated override to expose protected method: [RecipeProvider.has]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun has(item: ItemLike): Criterion<InventoryChangeTrigger.TriggerInstance> = super.has(item)

	/** Generated override to expose protected method: [RecipeProvider.has]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun has(tag: TagKey<Item>): Criterion<InventoryChangeTrigger.TriggerInstance> = super.has(tag)

	/** Generated override to expose protected method: [RecipeProvider.tag]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun tag(id: TagKey<Item>): Ingredient = super.tag(id)

	/** Generated override to expose protected method: [RecipeProvider.shaped]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun shaped(category: RecipeCategory, stack: ItemStackTemplate): ShapedRecipeBuilder = super.shaped(category, stack)

	/** Generated override to expose protected method: [RecipeProvider.shaped]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun shaped(category: RecipeCategory, item: ItemLike): ShapedRecipeBuilder = super.shaped(category, item)

	/** Generated override to expose protected method: [RecipeProvider.shaped]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun shaped(category: RecipeCategory, item: ItemLike, count: Int): ShapedRecipeBuilder = super.shaped(category, item, count)

	/** Generated override to expose protected method: [RecipeProvider.shapeless]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun shapeless(category: RecipeCategory, result: ItemStackTemplate): ShapelessRecipeBuilder = super.shapeless(category, result)

	/** Generated override to expose protected method: [RecipeProvider.shapeless]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun shapeless(category: RecipeCategory, item: ItemLike): ShapelessRecipeBuilder = super.shapeless(category, item)

	/** Generated override to expose protected method: [RecipeProvider.shapeless]  */
	@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
	public override fun shapeless(category: RecipeCategory, item: ItemLike, count: Int): ShapelessRecipeBuilder = super.shapeless(category, item, count) // GENERATED END

	companion object {
		const val DEFAULT_SMELT_TIME: Int = 200
		const val DEFAULT_BLAST_TIME: Int = DEFAULT_SMELT_TIME / 2
		const val DEFAULT_SMOKE_TIME: Int = DEFAULT_BLAST_TIME
		const val DEFAULT_CAMPFIRE_TIME: Int = DEFAULT_SMELT_TIME * 3

		/** Generated override to expose protected method: [RecipeProvider.insideOf]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
		@JvmStatic
		fun insideOf(block: Block): Criterion<EnterBlockTrigger.TriggerInstance> = RecipeProvider.insideOf(block)

		/** Generated override to expose protected method: [RecipeProvider.inventoryTrigger]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
		@JvmStatic
		fun inventoryTrigger(vararg predicates: ItemPredicate.Builder): Criterion<InventoryChangeTrigger.TriggerInstance> = RecipeProvider.inventoryTrigger(*predicates)

		/** Generated override to expose protected method: [RecipeProvider.inventoryTrigger]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
		@JvmStatic
		fun inventoryTrigger(vararg predicates: ItemPredicate): Criterion<InventoryChangeTrigger.TriggerInstance> = RecipeProvider.inventoryTrigger(*predicates)

		/** Generated override to expose protected method: [RecipeProvider.getHasName]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
		@JvmStatic
		fun getHasName(baseBlock: ItemLike): String = RecipeProvider.getHasName(baseBlock)

		/** Generated override to expose protected method: [RecipeProvider.getItemName]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
		@JvmStatic
		fun getItemName(itemLike: ItemLike): String = RecipeProvider.getItemName(itemLike)

		/** Generated override to expose protected method: [RecipeProvider.getSimpleRecipeName]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
		@JvmStatic
		fun getSimpleRecipeName(itemLike: ItemLike): String = RecipeProvider.getSimpleRecipeName(itemLike)

		/** Generated override to expose protected method: [RecipeProvider.getConversionRecipeName]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
		@JvmStatic
		fun getConversionRecipeName(product: ItemLike, material: ItemLike): String = RecipeProvider.getConversionRecipeName(product, material)

		/** Generated override to expose protected method: [RecipeProvider.getSmeltingRecipeName]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
		@JvmStatic
		fun getSmeltingRecipeName(product: ItemLike): String = RecipeProvider.getSmeltingRecipeName(product)

		/** Generated override to expose protected method: [RecipeProvider.getBlastingRecipeName]  */
		@Generated(value = ["com.modularmc.registrate.test.meta.UpdateRecipeProvider"], date = "Sun, 19 Apr 2026 10:13:58 GMT")
		@JvmStatic
		fun getBlastingRecipeName(product: ItemLike): String = RecipeProvider.getBlastingRecipeName(product)
	}
}
