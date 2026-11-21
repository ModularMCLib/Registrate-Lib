package com.modularmc.registrate.util

import com.modularmc.registrate.providers.generators.RegistrateRecipeProvider
import com.modularmc.registrate.providers.generators.RegistrateRecipeProvider.Companion.inventoryTrigger
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.advancements.Criterion
import net.minecraft.advancements.criterion.InventoryChangeTrigger.TriggerInstance
import net.minecraft.advancements.criterion.ItemPredicate
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.ItemLike

/** A vanilla ingredient with the recipe ID and criterion used during DataGen. */
class DataIngredient private constructor(private val ingredient: Ingredient, val id: Identifier, private val criterion: (RegistrateRecipeProvider) -> Criterion<TriggerInstance>) {
	fun getCriterion(provider: RegistrateRecipeProvider): Criterion<TriggerInstance> = criterion(provider)

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
		fun tag(tag: HolderSet.Named<Item>): DataIngredient = ingredient(Ingredient.of(tag), tag.key())

		@JvmStatic
		fun ingredient(ingredient: Ingredient, required: ItemLike): DataIngredient = DataIngredient(ingredient, BuiltInRegistries.ITEM.getKey(required.asItem())) { it.has(required) }

		@JvmStatic
		fun ingredient(ingredient: Ingredient, required: TagKey<Item>): DataIngredient = DataIngredient(ingredient, required.location()) { it.has(required) }

		@JvmStatic
		fun ingredient(ingredient: Ingredient, id: Identifier, vararg criteria: ItemPredicate): DataIngredient = DataIngredient(ingredient, id) { inventoryTrigger(*criteria) }
	}
}
