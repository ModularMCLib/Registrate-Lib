package com.modularmc.registrate.builders.base

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.localization.EntryLocalization
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.providers.DataGenContext
import com.modularmc.registrate.providers.RegistrateLangProvider
import com.modularmc.registrate.providers.RegistrateTagsProvider
import com.modularmc.registrate.providers.core.GeneratorType
import com.modularmc.registrate.providers.core.ProviderType
import com.modularmc.registrate.util.entry.EntryMetadata.DataGenerator
import com.modularmc.registrate.util.entry.LazyRegistryEntry
import com.modularmc.registrate.util.entry.RegistryEntry
import com.modularmc.registrate.util.nullness.NonNullBiConsumer
import com.modularmc.registrate.util.nullness.NonNullFunction
import com.modularmc.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.Registry
import net.minecraft.data.tags.TagsProvider
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagEntry
import net.minecraft.tags.TagKey
import net.neoforged.neoforge.registries.DeferredHolder

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.util.function.Consumer

abstract class AbstractBuilder<R : Any, T : R, P : Any, S : AbstractBuilder<R, T, P, S>>(
	override val owner: AbstractRegistrate<*>,
	override val parent: P,
	override val name: String,
	protected val callback: BuilderCallback,
	override val registryKey: ResourceKey<out Registry<R>>,
) : Builder<R, T, P, S> {
	private val tagsByType = Object2ObjectOpenHashMap<ProviderType<*>, ObjectOpenHashSet<TagKey<R>>>(2)
	private val dataGenerators = Object2ObjectLinkedOpenHashMap<GeneratorType<*>, DataGenerator<*>>(4)
	private var localization: EntryLocalization? = null
	private var submitted = false
	private val safeSupplier = LazyRegistryEntry(this)
	private var optional = false

	/** Invoked by the owner at registry time. */
	protected abstract fun createEntry(): T

	override fun register(): RegistryEntry<R, T> {
		val entry = callback.accept(name, registryKey, this, ::createEntry, ::createEntryWrapper)
		submitted = true
		dataGenerators.clear()
		return entry
	}

	fun dataGenerators(): Collection<DataGenerator<*>> = dataGenerators.values

	fun localization(): EntryLocalization? = localization

	override fun <D : Any> setData(type: GeneratorType<out D>, cons: NonNullBiConsumer<DataGenContext<R, T>, D>): S {
		if (submitted) return super.setData(type, cons)
		if (type === ProviderType.LANG) localization = null
		dataGenerators.putAndMoveToLast(type, DataGenerator(type) { provider -> cons.accept(DataGenContext.from(this), provider) })
		return self()
	}

	override fun <D : Any> removeData(type: GeneratorType<out D>): S {
		if (submitted) return super.removeData(type)
		if (type === ProviderType.LANG) localization = null
		dataGenerators.remove(type)
		return self()
	}

	protected open fun createEntryWrapper(delegate: DeferredHolder<R, T>): RegistryEntry<R, T> = RegistryEntry(owner, delegate)

	override fun asSupplier(): NonNullSupplier<T> = safeSupplier

	fun <TP> tag(type: ProviderType<out TP>, vararg tags: TagKey<R>): S where TP : TagsProvider<R>, TP : RegistrateTagsProvider<R> {
		val keys = tagsByType.getOrPut(type) {
			val registered = ObjectOpenHashSet<TagKey<R>>()
			setData(type) { _, provider ->
				val entry = asTag()
				for (key in registered) provider.rawBuilder(key).add(entry)
			}
			registered
		}
		keys.addAll(tags.asList())
		return self()
	}

	open fun asOptional(): S {
		optional = true
		return self()
	}

	protected open fun asTag(): TagEntry {
		val id = Identifier.fromNamespaceAndPath(owner.modid, name)
		return if (optional) TagEntry.optionalElement(id) else TagEntry.element(id)
	}

	fun <TP> removeTag(type: ProviderType<TP>, vararg tags: TagKey<R>): S where TP : TagsProvider<R>, TP : RegistrateTagsProvider<R> {
		val keys = tagsByType[type] ?: return self()
		for (key in tags) keys.remove(key)
		return self()
	}

	open fun lang(langKeyProvider: NonNullFunction<T, String>): S = defaultLang(langKeyProvider, name)

	open fun lang(langKeyProvider: NonNullFunction<T, String>, name: String): S = localization(
		langKeyProvider,
		{ LocalizationDeclaration.sourceText(name, owner.localizationDefaultValues()) },
		Kind.DECLARED,
	)

	protected open fun defaultLang(key: NonNullFunction<T, String>, path: String): S =
		localization(key, { LocalizationDeclaration.sourceText(RegistrateLangProvider.toEnglishName(path), owner.localizationDefaultValues()) }, Kind.AUTOMATIC)

	/** Declares text whose translation key is read from the registered object during DataGen. */
	open fun localization(key: NonNullFunction<T, String>, configure: Consumer<LocalizationDeclaration.Builder>): S {
		val declaration = LocalizationDeclaration.Builder().also(configure::accept).build(owner.localizationDefaultValues())
		return localization(key, { declaration }, Kind.DECLARED)
	}

	private fun localization(key: NonNullFunction<T, String>, declaration: NonNullSupplier<LocalizationDeclaration>, kind: Kind): S {
		val builder = removeData(ProviderType.LANG)
		val entry = asSupplier()
		localization = EntryLocalization({ key.apply(entry.get()) }, declaration, kind)
		return builder
	}

	open val resourceKey: ResourceKey<R>
		get() = ResourceKey.create(registryKey, Identifier.fromNamespaceAndPath(owner.modid, name))

	@Suppress("UNCHECKED_CAST")
	private fun self(): S = this as S
}
