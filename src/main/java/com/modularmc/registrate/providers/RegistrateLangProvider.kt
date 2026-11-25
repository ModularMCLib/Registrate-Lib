package com.modularmc.registrate.providers

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.localization.LocalizationDeclaration
import com.modularmc.registrate.localization.TranslationKey
import com.modularmc.registrate.localization.TranslationSource
import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.localization.annotation.AnnotatedTranslations
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

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.apache.commons.lang3.StringUtils

import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

/** 收集所属模组的语言声明，交由本地化解析流程按各语言和命名空间写文件。 */
open class RegistrateLangProvider(private val owner: AbstractRegistrate<*>, private val packOutput: PackOutput) :
	LanguageProvider(packOutput, owner.modid, "en_us"),
	RegistrateProvider {
	private class Operation(val key: String, val value: String)

	private val operations = ObjectArrayList<Operation>()

	/** 语言文件作为客户端资源生成。 */
	override fun getSide(): LogicalSide = LogicalSide.CLIENT

	/** 返回语言声明收集阶段使用的日志名称。 */
	override fun getName(): String = "Registrate language declarations"

	/** 调用所属模组为语言生成器登记的回调，收集其追加的文本。 */
	override fun addTranslations() {
		owner.genData(ProviderType.LANG, this)
	}

	/** 用已注册条目标识的路径推导默认名称。取值器应提供指定原版注册表中已绑定的条目。 */
	@Suppress("UNCHECKED_CAST")
	open fun <T : Any> getAutomaticName(supplier: NonNullSupplier<out T>, registry: ResourceKey<out Registry<T>>): String {
		val entries = BuiltInRegistries.REGISTRY.getValue(registry.identifier()) as Registry<T>
		return toEnglishName(entries.getKey(supplier.get())!!.path)
	}

	/** 为方块登记从注册名称推导的默认显示名。 */
	open fun addBlock(block: NonNullSupplier<out Block>) {
		addBlock(block, getAutomaticName(block, Registries.BLOCK))
	}

	/** 为方块登记默认显示名，并为它的方块物品登记单行提示文本。 */
	open fun addBlockWithTooltip(block: NonNullSupplier<out Block>, tooltip: String) {
		addBlock(block)
		addTooltip(block, tooltip)
	}

	/** 为方块登记指定显示名，并为它的方块物品登记单行提示文本。 */
	open fun addBlockWithTooltip(block: NonNullSupplier<out Block>, name: String, tooltip: String) {
		addBlock(block, name)
		addTooltip(block, tooltip)
	}

	/** 为物品登记从注册名称推导的默认显示名。 */
	open fun addItem(item: NonNullSupplier<out Item>) {
		addItem(item, getAutomaticName(item, Registries.ITEM))
	}

	/** 同时登记物品显示名和按顺序编号的提示文本。 */
	open fun addItemWithTooltip(item: NonNullSupplier<out Item>, name: String, tooltip: List<String>) {
		addItem(item, name)
		addTooltip(item, tooltip)
	}

	/** 使用物品翻译键的 `.desc` 后缀登记单行提示文本。 */
	open fun addTooltip(item: NonNullSupplier<out ItemLike>, tooltip: String) {
		add("${item.get().asItem().descriptionId}.desc", tooltip)
	}

	/** 按列表顺序登记多行提示文本，翻译键使用 `.desc.0` 起的连续编号。 */
	open fun addTooltip(item: NonNullSupplier<out ItemLike>, tooltip: List<String>) {
		if (tooltip.isEmpty()) return
		val prefix = "${item.get().asItem().descriptionId}.desc."
		tooltip.forEachIndexed { index, text -> add("$prefix$index", text) }
	}

	/** 以标签页标题组件中已有的翻译键登记显示名。标题组件应使用可翻译内容。 */
	open fun add(tab: CreativeModeTab, name: String) {
		add((tab.displayName.contents as TranslatableContents).key, name)
	}

	/** 暂存一条键与源文，收集语言声明时将其解释为所属模组源语言的文本。 */
	override fun add(key: String, value: String) {
		operations.add(Operation(key, value))
	}

	internal fun collect(registry: TranslationRegistry) {
		val modId = owner.modid
		val locale = owner.requireSourceLocale()
		val terminology = owner.terminology()
		owner.configureLocaleDerivations(registry)
		owner.collectEntryTranslations(registry, locale)
		addTranslations()
		for (operation in operations) {
			val key = operation.key
			val source = TranslationSource(Kind.DECLARED, "[$modId] ProviderType.LANG ($key)")
			registry.add(
				TranslationKey(modId, key),
				locale,
				LocalizationDeclaration.sourceText(operation.value, owner.localizationDefaultValues()),
				source,
				terminology,
			)
		}
		operations.clear()
		owner.collectRawTranslations(registry, locale)
		AnnotatedTranslations.collect(owner, locale, registry)
		owner.collectImportedTranslations(registry, locale)
	}

	/** 为实体类型登记从注册名称推导的默认显示名。 */
	open fun addEntityType(entity: NonNullSupplier<out EntityType<*>>) {
		addEntityType(entity, getAutomaticName(entity, Registries.ENTITY_TYPE))
	}

	/** 收集当前模组的语言声明，解析并异步写出各语言文件。 */
	override fun run(cache: CachedOutput): CompletableFuture<*> {
		val registry = TranslationRegistry()
		collect(registry)
		return LanguageOutput.write(cache, packOutput, registry)
	}

	companion object {
		private val underscores = Pattern.compile("_")

		/** 将带下划线的标识拆成单词，首字母大写后用空格连接。 */
		@JvmStatic
		fun toEnglishName(internalName: String): String = underscores.split(internalName.lowercase()).joinToString(" ", transform = StringUtils::capitalize)
	}
}
