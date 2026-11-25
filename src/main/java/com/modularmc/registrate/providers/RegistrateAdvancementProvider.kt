package com.modularmc.registrate.providers

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.providers.core.RegistrateProvider

import net.minecraft.advancements.Advancement
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.neoforged.fml.LogicalSide
import net.neoforged.neoforge.common.conditions.ICondition
import net.neoforged.neoforge.common.conditions.WithConditions

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

/** 在同一轮数据生成中收集进度定义及其启用条件，并写入所属模组的输出目录。 */
open class RegistrateAdvancementProvider(private val owner: AbstractRegistrate<*>, packOutput: PackOutput, private val registriesLookup: CompletableFuture<HolderLookup.Provider>) :
	RegistrateProvider,
	Consumer<AdvancementHolder> {
	private val paths = packOutput.createPathProvider(PackOutput.Target.DATA_PACK, "advancement")
	private val advancementsToSave = ArrayList<CompletableFuture<*>>()
	private val seenAdvancements = ObjectOpenHashSet<Identifier>()
	private var cache: CachedOutput? = null

	lateinit var provider: HolderLookup.Provider
		private set

	/** 在进度生成回调中按注册键查询引用。缺少注册表或条目时抛出异常。 */
	open fun <T : Any> resolve(key: ResourceKey<T>): Holder<T> = provider.lookupOrThrow(key.registryKey()).getOrThrow(key)

	/** 进度作为服务端数据生成。 */
	override fun getSide(): LogicalSide = LogicalSide.SERVER

	/** 为指定进度登记标题源文，返回可放入进度定义的翻译组件。[category] 用作命名空间。 */
	open fun title(category: String, name: String, title: String): MutableComponent = owner.addLang("advancements", Identifier.fromNamespaceAndPath(category, name), "title", title)

	/** 为指定进度登记说明源文，返回可放入进度定义的翻译组件。[category] 用作命名空间。 */
	open fun desc(category: String, name: String, desc: String): MutableComponent = owner.addLang("advancements", Identifier.fromNamespaceAndPath(category, name), "description", desc)

	/** 等待注册表查询就绪，执行进度生成回调，并等到全部进度文件写完后完成。 */
	override fun run(cache: CachedOutput): CompletableFuture<*> = registriesLookup.thenCompose { lookup ->
		provider = lookup
		advancementsToSave.clear()
		seenAdvancements.clear()
		this.cache = cache
		try {
			owner.genData(ProviderType.ADVANCEMENT, this)
		} finally {
			this.cache = null
		}
		CompletableFuture.allOf(*advancementsToSave.toTypedArray())
	}

	/** 在进度生成回调内提交一个始终启用的进度。 */
	override fun accept(holder: AdvancementHolder) {
		withConditions(holder, emptyList())
	}

	/**
	 * 在进度生成回调内提交带加载条件的进度。
	 * @param holder 包含标识和定义的进度
	 * @param conditions 数据包加载时应用的条件
	 * @throws IllegalStateException 不在生成回调中，或已提交相同标识的进度
	 */
	open fun withConditions(holder: AdvancementHolder, conditions: List<ICondition>) {
		val cache = checkNotNull(cache) { "Advancements must be submitted during their DataGen callback" }
		check(seenAdvancements.add(holder.id())) { "Duplicate advancement ${holder.id()}" }
		val path = paths.json(holder.id())
		val advancement = holder.value()
		val write = if (conditions.isEmpty()) {
			DataProvider.saveStable(cache, provider, Advancement.CODEC, advancement, path)
		} else {
			DataProvider.saveStable(cache, provider, Advancement.CONDITIONAL_CODEC, Optional.of(WithConditions(conditions, advancement)), path)
		}
		advancementsToSave.add(write)
	}

	/** 返回数据生成日志中使用的进度提供器名称。 */
	override fun getName(): String = "Advancements"
}
