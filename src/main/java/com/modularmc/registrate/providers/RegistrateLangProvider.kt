package com.modularmc.registrate.providers

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.localization.index.IndexedTranslations
import com.modularmc.registrate.localization.resolution.TranslationRegistry
import com.modularmc.registrate.providers.core.LanguageOutput
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.providers.core.RegistrateProvider
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.CachedOutput
import net.minecraft.data.PackOutput
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.block.Block
import net.neoforged.fml.LogicalSide
import net.neoforged.neoforge.common.data.LanguageProvider

import org.apache.commons.lang3.StringUtils

import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

open class RegistrateLangProvider(private val owner: AbstractRegistrate<*>, private val packOutput: PackOutput) :
	LanguageProvider(packOutput, owner.modid, "en_us"),
	RegistrateProvider {
	private class Operation(val key: String, val value: String)

	private val operations = ArrayList<Operation>()

	override fun getSide(): LogicalSide = LogicalSide.CLIENT

	override fun getName(): String = "Registrate language declarations"

	override fun addTranslations() {
		owner.genData(ProviderType.LANG, this)
	}

	@Suppress("UNCHECKED_CAST")
	open fun <T : Any> getAutomaticName(supplier: NonNullSupplier<out T>, registry: ResourceKey<out Registry<T>>): String {
		val entries = BuiltInRegistries.REGISTRY.getValue(registry.identifier()) as Registry<T>
		return toEnglishName(entries.getKey(supplier.get())!!.path)
	}

	open fun addBlock(block: NonNullSupplier<out Block>) {
		addBlock(block, getAutomaticName(block, Registries.BLOCK))
	}

	open fun addBlockWithTooltip(block: NonNullSupplier<out Block>, tooltip: String) {
		addBlock(block)
		addTooltip(block, tooltip)
	}

	open fun addBlockWithTooltip(block: NonNullSupplier<out Block>, name: String, tooltip: String) {
		addBlock(block, name)
		addTooltip(block, tooltip)
	}

	open fun addItem(item: NonNullSupplier<out Item>) {
		addItem(item, getAutomaticName(item, Registries.ITEM))
	}

	open fun addItemWithTooltip(item: NonNullSupplier<out Item>, name: String, tooltip: List<String>) {
		addItem(item, name)
		addTooltip(item, tooltip)
	}

	open fun addTooltip(item: NonNullSupplier<out ItemLike>, tooltip: String) {
		add("${item.get().asItem().descriptionId}.desc", tooltip)
	}

	open fun addTooltip(item: NonNullSupplier<out ItemLike>, tooltip: List<String>) {
		if (tooltip.isEmpty()) return
		val prefix = "${item.get().asItem().descriptionId}.desc."
		tooltip.forEachIndexed { index, text -> add("$prefix$index", text) }
	}

	open fun add(tab: CreativeModeTab, name: String) {
		add((tab.displayName.contents as TranslatableContents).key, name)
	}

	override fun add(key: String, value: String) {
		operations.add(Operation(key, value))
	}

	open fun collect(registry: TranslationRegistry) {
		val modId = owner.modid
		val locale = owner.requireSourceLocale()
		val terminology = owner.terminology()
		owner.collectEntryTranslations(registry, locale)
		addTranslations()
		for (operation in operations) {
			val key = operation.key
			val source = TranslationSource(Kind.DECLARED, "[$modId] ProviderType.LANG ($key)")
			registry.add(TranslationKey(modId, key), locale, LocalizationDeclaration.sourceText(operation.value), source, terminology)
		}
		operations.clear()
		owner.collectRawTranslations(registry, locale)
		IndexedTranslations.collect(modId, locale, registry, terminology)
	}

	open fun addEntityType(entity: NonNullSupplier<out EntityType<*>>) {
		addEntityType(entity, getAutomaticName(entity, Registries.ENTITY_TYPE))
	}

	override fun run(cache: CachedOutput): CompletableFuture<*> {
		val registry = TranslationRegistry()
		collect(registry)
		return LanguageOutput.write(cache, packOutput, registry)
	}

	companion object {
		private val underscores = Pattern.compile("_")

		@JvmStatic
		fun toEnglishName(internalName: String): String = underscores.split(internalName.lowercase()).joinToString(" ", transform = StringUtils::capitalize)
	}
}
