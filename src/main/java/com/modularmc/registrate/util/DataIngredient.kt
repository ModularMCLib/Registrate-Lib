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

/** 封装原版配方材料，并携带数据生成所用的配方标识和解锁条件。 */
class DataIngredient private constructor(private val ingredient: Ingredient, val id: Identifier, private val criterion: (RegistrateRecipeProvider) -> Criterion<TriggerInstance>) {
	/** 用本轮的配方生成器构造此材料附带的解锁条件。 */
	fun getCriterion(provider: RegistrateRecipeProvider): Criterion<TriggerInstance> = criterion(provider)

	/** 返回底层的原版材料匹配规则，供原版配方构建器使用。 */
	fun toVanilla(): Ingredient = ingredient

	companion object {
		/** 在调用时解析各物品取值器。第一个物品决定材料标识和解锁条件，其余物品也可作为配方输入。 */
		@JvmStatic
		fun <T : ItemLike> items(first: NonNullSupplier<out T>, vararg others: NonNullSupplier<out T>): DataIngredient {
			val required = first.get()
			val inputs = Array<ItemLike>(others.size + 1) { index -> if (index == 0) required else others[index - 1].get() }
			return ingredient(Ingredient.of(*inputs), required)
		}

		/** 创建能匹配任一给定物品的材料。使用第一个物品的标识和持有条件。 */
		@JvmStatic
		fun <T : ItemLike> items(first: T, vararg others: T): DataIngredient {
			val inputs = Array<ItemLike>(others.size + 1) { index -> if (index == 0) first else others[index - 1] }
			return ingredient(Ingredient.of(*inputs), first)
		}

		/** 使用物品标签作为匹配材料，并按同一个标签生成标识和解锁条件。 */
		@JvmStatic
		fun tag(tag: HolderSet.Named<Item>): DataIngredient = ingredient(Ingredient.of(tag), tag.key())

		/** 保留原有材料的匹配规则，以指定物品的注册标识命名，并以持有该物品为解锁条件。 */
		@JvmStatic
		fun ingredient(ingredient: Ingredient, required: ItemLike): DataIngredient = DataIngredient(ingredient, BuiltInRegistries.ITEM.getKey(required.asItem())) { it.has(required) }

		/** 保留原有材料的匹配规则，以指定标签的标识命名，并以持有标签中的物品为解锁条件。 */
		@JvmStatic
		fun ingredient(ingredient: Ingredient, required: TagKey<Item>): DataIngredient = DataIngredient(ingredient, required.location()) { it.has(required) }

		/** 使用显式材料标识，并将给定物品谓词用于构造物品栏变化的解锁条件。 */
		@JvmStatic
		fun ingredient(ingredient: Ingredient, id: Identifier, vararg criteria: ItemPredicate): DataIngredient = DataIngredient(ingredient, id) { inventoryTrigger(*criteria) }
	}
}
