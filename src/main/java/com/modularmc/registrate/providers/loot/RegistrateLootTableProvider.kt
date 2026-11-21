package com.modularmc.registrate.providers.loot

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.providers.core.RegistrateProvider
import com.modularmc.registrate.util.nullness.NonNullConsumer

import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.WritableRegistry
import net.minecraft.data.PackOutput
import net.minecraft.data.loot.LootTableProvider
import net.minecraft.data.loot.LootTableSubProvider
import net.minecraft.resources.ResourceKey
import net.minecraft.util.ProblemReporter
import net.minecraft.util.context.ContextKeySet
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.ValidationContextSource
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.neoforged.fml.LogicalSide

import com.google.common.collect.HashMultimap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.apache.commons.lang3.function.TriFunction

import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import java.util.function.Consumer

open class RegistrateLootTableProvider(private val parent: AbstractRegistrate<*>, output: PackOutput, private val lookup: CompletableFuture<HolderLookup.Provider>) :
	LootTableProvider(output, emptySet(), emptyList(), lookup),
	RegistrateProvider {
	/** Defines the context and factory for a family of loot tables. */
	interface LootType<T : RegistrateLootTables> {
		fun getLootCreator(provider: HolderLookup.Provider, parent: AbstractRegistrate<*>, callback: Consumer<T>): T

		fun getLootSet(): ContextKeySet

		companion object {
			@JvmField
			val BLOCK = register("block", LootContextParamSets.BLOCK, ::RegistrateBlockLootTables)

			@JvmField
			val ENTITY = register("entity", LootContextParamSets.ENTITY, ::RegistrateEntityLootTables)

			@JvmStatic
			fun <T : RegistrateLootTables> register(name: String, set: ContextKeySet, factory: TriFunction<HolderLookup.Provider, AbstractRegistrate<*>, Consumer<T>, T>): LootType<T> {
				val type = object : LootType<T> {
					override fun getLootCreator(provider: HolderLookup.Provider, parent: AbstractRegistrate<*>, callback: Consumer<T>): T = factory.apply(provider, parent, callback)

					override fun getLootSet(): ContextKeySet = set
				}
				lootTypes[name] = type
				return type
			}
		}
	}

	private val specialLootActions = HashMultimap.create<LootType<*>, Consumer<RegistrateLootTables>>()
	private val lootActions = HashMultimap.create<ContextKeySet, Consumer<BiConsumer<ResourceKey<LootTable>, LootTable.Builder>>>()
	private val currentLootCreators = ObjectOpenHashSet<RegistrateLootTables>()

	open fun getProvider(): HolderLookup.Provider = checkNotNull(lookup.getNow(null)) { "Loot table registry lookup is not ready" }

	open fun <T : Any> resolve(key: ResourceKey<T>): Holder<T> = getProvider().lookupOrThrow(key.registryKey()).getOrThrow(key)

	override fun getSide(): LogicalSide = LogicalSide.SERVER

	override fun validate(tables: WritableRegistry<LootTable>, validationContext: ValidationContextSource, problems: ProblemReporter.Collector) {
		super.validate(tables, validationContext, problems)
		for (creator in currentLootCreators) creator.validate(tables, validationContext)
	}

	@Suppress("UNCHECKED_CAST")
	open fun <T : RegistrateLootTables> addLootAction(type: LootType<T>, action: NonNullConsumer<T>) {
		specialLootActions.put(type, action as Consumer<RegistrateLootTables>)
	}

	open fun addLootAction(set: ContextKeySet, action: Consumer<BiConsumer<ResourceKey<LootTable>, LootTable.Builder>>) {
		lootActions.put(set, action)
	}

	private fun <T : RegistrateLootTables> getLootCreator(provider: HolderLookup.Provider, type: LootType<T>): LootTableSubProvider {
		val creator = type.getLootCreator(provider, parent) { tables ->
			for (action in specialLootActions[type]) action.accept(tables)
		}
		currentLootCreators.add(creator)
		return creator
	}

	override fun getTables(): List<SubProviderEntry> {
		parent.genData(ProviderType.LOOT, this)
		currentLootCreators.clear()
		return buildList(lootTypes.size + lootActions.keySet().size) {
			for (type in lootTypes.values) {
				add(SubProviderEntry({ getLootCreator(it, type) }, type.getLootSet()))
			}
			for ((set, actions) in lootActions.asMap()) {
				add(SubProviderEntry({ LootTableSubProvider { callback -> actions.forEach { it.accept(callback) } } }, set))
			}
		}
	}

	companion object {
		private val lootTypes = Object2ObjectOpenHashMap<String, LootType<*>>()
	}
}
