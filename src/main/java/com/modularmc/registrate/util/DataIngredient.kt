package com.modularmc.registrate.util

import com.modularmc.registrate.providers.generators.RegistrateRecipeProvider
import com.modularmc.registrate.providers.generators.RegistrateRecipeProvider.Companion.has
import com.modularmc.registrate.providers.generators.RegistrateRecipeProvider.Companion.inventoryTrigger
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.advancements.Criterion
import net.minecraft.advancements.critereon.InventoryChangeTrigger.TriggerInstance
import net.minecraft.advancements.critereon.ItemPredicate
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.ItemLike

import java.util.function.Supplier

/** A vanilla ingredient with the recipe ID and criterion used during DataGen. */
class DataIngredient private constructor(private val ingredient: Ingredient, val id: ResourceLocation, private val criterion: Supplier<Criterion<TriggerInstance>>) {
	@Suppress("UNUSED_PARAMETER")
	fun getCriterion(provider: RegistrateRecipeProvider): Criterion<TriggerInstance> = criterion.get()

	fun toVanilla(): Ingredient = ingredient

	companion object {
		@JvmStatic
		fun <T : ItemLike> items(first: NonNullSupplier<out T>, vararg others: NonNullSupplier<out T>): DataIngredient {
			val required = first.get()
			val inputs = Array<ItemLike>(others.size + 1) { index -> if (index == 0) required else others[index - 1].get() }
			return ingredient(Ingredient.of(*inputs), required)
		}

		@JvmStatic
		fun <T : ItemLike> items(first: T, vararg others: T): DataIngredient {
			val inputs = Array<ItemLike>(others.size + 1) { index -> if (index == 0) first else others[index - 1] }
			return ingredient(Ingredient.of(*inputs), first)
		}

		@JvmStatic
		fun tag(tag: HolderSet.Named<Item>): DataIngredient = tag(tag.key())

		@JvmStatic
		fun tag(tag: TagKey<Item>): DataIngredient = ingredient(Ingredient.of(tag), tag)

		@JvmStatic
		fun ingredient(ingredient: Ingredient, required: ItemLike): DataIngredient = DataIngredient(ingredient, BuiltInRegistries.ITEM.getKey(required.asItem())) { has(required) }

		@JvmStatic
		fun ingredient(ingredient: Ingredient, required: TagKey<Item>): DataIngredient = DataIngredient(ingredient, required.location()) { has(required) }

		@JvmStatic
		fun ingredient(ingredient: Ingredient, id: ResourceLocation, vararg criteria: ItemPredicate): DataIngredient = DataIngredient(ingredient, id) { inventoryTrigger(*criteria) }
	}
}
