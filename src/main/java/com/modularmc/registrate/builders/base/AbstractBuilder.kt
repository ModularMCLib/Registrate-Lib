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
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagEntry
import net.minecraft.tags.TagKey
import net.neoforged.neoforge.registries.DeferredHolder

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.util.function.Consumer

/** 保存条目提交前的标签、本地化及数据生成配置，供具体条目构建器复用。构建器由模组初始化线程配置。 */
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

	/** 由所属 Registrate 在注册表处理该条目时调用，负责创建实际对象。 */
	protected abstract fun createEntry(): T

	/** 提交条目工厂和元数据，返回尚待注册事件绑定的引用。提交后清空构建器暂存的数据生成回调。 */
	override fun register(): RegistryEntry<R, T> {
		val entry = callback.accept(name, registryKey, this, ::createEntry, ::createEntryWrapper)
		submitted = true
		dataGenerators.clear()
		return entry
	}

	/** 返回当前暂存的数据生成回调。注册提交时会将这些回调转交给所属 Registrate。 */
	fun dataGenerators(): Collection<DataGenerator<*>> = dataGenerators.values

	/** 返回当前构建器的本地化声明。没有声明时返回 `null`。 */
	fun localization(): EntryLocalization? = localization

	/** 设置或替换指定类型的生成回调。提交前暂存在构建器中，提交后更新所属 Registrate 的回调。 */
	override fun <D : Any> setData(type: GeneratorType<out D>, cons: NonNullBiConsumer<DataGenContext<R, T>, D>): S {
		if (submitted) return super.setData(type, cons)
		if (type === ProviderType.LANG) localization = null
		dataGenerators.putAndMoveToLast(type, DataGenerator(type) { provider -> cons.accept(DataGenContext.from(this), provider) })
		return self()
	}

	/** 移除指定类型的生成回调。提交前移除语言生成回调时也会清除构建器的本地化声明。 */
	override fun <D : Any> removeData(type: GeneratorType<out D>): S {
		if (submitted) return super.removeData(type)
		if (type === ProviderType.LANG) localization = null
		dataGenerators.remove(type)
		return self()
	}

	protected open fun createEntryWrapper(delegate: DeferredHolder<R, T>): RegistryEntry<R, T> = RegistryEntry(owner, delegate)

	/** 返回可提前保存的条目取值器。调用它取值时，条目须已注册并绑定。 */
	override fun asSupplier(): NonNullSupplier<T> = safeSupplier

	/** 为当前条目追加标签。生成时由 [type] 对应的提供器写入，重复的标签键会合并。 */
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

	/** 将当前条目写入标签时标记为可选，供条目可能缺失的运行环境使用。 */
	open fun asOptional(): S {
		optional = true
		return self()
	}

	protected open fun asTag(): TagEntry {
		val id = ResourceLocation.fromNamespaceAndPath(owner.modid, name)
		return if (optional) TagEntry.optionalElement(id) else TagEntry.element(id)
	}

	/** 从当前构建器配置中移除指定提供器下的标签。 */
	fun <TP> removeTag(type: ProviderType<TP>, vararg tags: TagKey<R>): S where TP : TagsProvider<R>, TP : RegistrateTagsProvider<R> {
		val keys = tagsByType[type] ?: return self()
		for (key in tags) keys.remove(key)
		return self()
	}

	/** 用条目名称生成默认源文，由 [langKeyProvider] 从已绑定对象取得翻译键。 */
	open fun lang(langKeyProvider: NonNullFunction<T, String>): S = defaultLang(langKeyProvider, name)

	/** 为翻译键登记显式源文。键在生成阶段从已绑定对象读取，声明采用所属模组的默认元数据。 */
	open fun lang(langKeyProvider: NonNullFunction<T, String>, name: String): S = localization(
		langKeyProvider,
		{ LocalizationDeclaration.sourceText(name, owner.localizationDefaultValues()) },
		Kind.DECLARED,
	)

	protected open fun defaultLang(key: NonNullFunction<T, String>, path: String): S =
		localization(key, { LocalizationDeclaration.sourceText(RegistrateLangProvider.toEnglishName(path), owner.localizationDefaultValues()) }, Kind.AUTOMATIC)

	/** 登记本地化声明。数据生成时从已绑定的注册对象解析翻译键。 */
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
		get() = ResourceKey.create(registryKey, ResourceLocation.fromNamespaceAndPath(owner.modid, name))

	@Suppress("UNCHECKED_CAST")
	private fun self(): S = this as S
}
