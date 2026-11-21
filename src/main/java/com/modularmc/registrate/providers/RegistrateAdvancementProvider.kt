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

open class RegistrateAdvancementProvider(private val owner: AbstractRegistrate<*>, packOutput: PackOutput, private val registriesLookup: CompletableFuture<HolderLookup.Provider>) :
	RegistrateProvider,
	Consumer<AdvancementHolder> {
	private val paths = packOutput.createPathProvider(PackOutput.Target.DATA_PACK, "advancement")
	private val advancementsToSave = ArrayList<CompletableFuture<*>>()
	private val seenAdvancements = ObjectOpenHashSet<Identifier>()
	private var cache: CachedOutput? = null

	lateinit var provider: HolderLookup.Provider
		private set

	open fun <T : Any> resolve(key: ResourceKey<T>): Holder<T> = provider.lookupOrThrow(key.registryKey()).getOrThrow(key)

	override fun getSide(): LogicalSide = LogicalSide.SERVER

	open fun title(category: String, name: String, title: String): MutableComponent = owner.addLang("advancements", Identifier.fromNamespaceAndPath(category, name), "title", title)

	open fun desc(category: String, name: String, desc: String): MutableComponent = owner.addLang("advancements", Identifier.fromNamespaceAndPath(category, name), "description", desc)

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

	override fun accept(holder: AdvancementHolder) {
		withConditions(holder, emptyList())
	}

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

	override fun getName(): String = "Advancements"
}
