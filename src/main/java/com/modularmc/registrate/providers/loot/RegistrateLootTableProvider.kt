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

/** 为所属模组组织各类掉落表的生成任务，并在收集后运行原版及自定义校验。 */
open class RegistrateLootTableProvider(private val parent: AbstractRegistrate<*>, output: PackOutput, private val lookup: CompletableFuture<HolderLookup.Provider>) :
	LootTableProvider(output, emptySet(), emptyList(), lookup),
	RegistrateProvider {
	/** 定义一类掉落表所使用的上下文参数和生成器工厂。 */
	interface LootType<T : RegistrateLootTables> {
		/** 从本轮查询和所属模组创建掉落表生成器，在生成阶段调用 [callback] 收集该类掉落表。 */
		fun getLootCreator(provider: HolderLookup.Provider, parent: AbstractRegistrate<*>, callback: Consumer<T>): T

		/** 返回此类掉落表允许使用的上下文参数集合。 */
		fun getLootSet(): ContextKeySet

		companion object {
			@JvmField
			val BLOCK = register("block", LootContextParamSets.BLOCK, ::RegistrateBlockLootTables)

			@JvmField
			val ENTITY = register("entity", LootContextParamSets.ENTITY, ::RegistrateEntityLootTables)

			/** 登记一类掉落表的参数集合与生成器工厂。同名登记会替换该类的定义。 */
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

	/** 返回本轮已就绪的注册表查询。查询尚未完成时抛出 [IllegalStateException]。 */
	open fun getProvider(): HolderLookup.Provider = checkNotNull(lookup.getNow(null)) { "Loot table registry lookup is not ready" }

	/** 按条目的注册键查询引用。缺少注册表或条目时抛出异常。 */
	open fun <T : Any> resolve(key: ResourceKey<T>): Holder<T> = getProvider().lookupOrThrow(key.registryKey()).getOrThrow(key)

	/** 掉落表作为服务端数据生成。 */
	override fun getSide(): LogicalSide = LogicalSide.SERVER

	/** 先按原版规则校验全部掉落表，再调用本轮各子生成器的校验入口。 */
	override fun validate(tables: WritableRegistry<LootTable>, validationContext: ValidationContextSource, problems: ProblemReporter.Collector) {
		super.validate(tables, validationContext, problems)
		for (creator in currentLootCreators) creator.validate(tables, validationContext)
	}

	/** 为指定掉落表类型追加生成回调。回调接收该类型的生成器。 */
	@Suppress("UNCHECKED_CAST")
	open fun <T : RegistrateLootTables> addLootAction(type: LootType<T>, action: NonNullConsumer<T>) {
		specialLootActions.put(type, action as Consumer<RegistrateLootTables>)
	}

	/** 按上下文参数集合追加生成回调。回调通过接收器提交掉落表键和构建器。 */
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

	/** 收集所属模组的掉落表配置，并返回按类型和上下文创建的子生成任务。 */
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
