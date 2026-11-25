package com.modularmc.registrate.providers.generators

import net.minecraft.client.data.models.model.ModelInstance
import net.minecraft.client.data.models.model.TextureMapping
import net.minecraft.client.data.models.model.TextureSlot
import net.minecraft.client.resources.model.UnbakedModel.GuiLight
import net.minecraft.client.resources.model.sprite.Material
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.client.model.generators.template.CustomLoaderBuilder
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder
import net.neoforged.neoforge.client.model.generators.template.RootTransformsBuilder
import net.neoforged.neoforge.client.model.generators.template.TransformVecBuilder

import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Supplier

/** 在同一构建器内调整扩展模型模板和纹理映射，完成后写入当前模型输出。 */
class RegistrateLegacyBlockModelBuilder internal constructor(private val output: BiConsumer<Identifier, ModelInstance>, private val template: ExtendedModelTemplateBuilder, texture: TextureMapping) {
	private val texture: TextureMapping = texture.copy()

	/** 为指定的纹理槽设置材质，再返回当前构建器。 */
	fun texture(slot: TextureSlot, texture: Material): RegistrateLegacyBlockModelBuilder {
		this.template.requiredTextureSlot(slot)
		this.texture.put(slot, texture)
		return this
	}

	/** 立即执行 [action]，对正在配置的模板构建器做进一步调整。 */
	fun transformTemplate(action: Consumer<ExtendedModelTemplateBuilder>): RegistrateLegacyBlockModelBuilder {
		action.accept(template)
		return this
	}

	/** 立即执行 [action]，调整当前模型的纹理映射。 */
	fun transformTexture(action: Consumer<TextureMapping>): RegistrateLegacyBlockModelBuilder {
		action.accept(texture)
		return this
	}

	/** 按方块的注册标识生成模型并返回写出的模型标识。 */
	fun build(block: Block): Identifier = template.build().create(block, texture, output)

	/** 在 [loc] 指定的位置生成模型，返回实际写出的模型标识。 */
	fun build(loc: Identifier): Identifier = template.build().create(loc, texture, output)

	// 以下方法将模型配置交给模板构建器。

	/**
	 * 指定模型继承属性时使用的父模型。
	 */
	fun parent(parent: Identifier): RegistrateLegacyBlockModelBuilder {
		template.parent(parent)
		return this
	}

	/**
	 * 设置附加到模型文件路径末尾的后缀。
	 */
	fun suffix(suffix: String): RegistrateLegacyBlockModelBuilder {
		template.suffix(suffix)
		return this
	}

	/**
	 * 为指定的物品展示场景配置变换。
	 *
	 * @param type 目标展示场景
	 * @param action 用于配置该场景变换的回调
	 * @return 当前构建器
	 */
	fun transform(type: ItemDisplayContext, action: Consumer<TransformVecBuilder>): RegistrateLegacyBlockModelBuilder {
		template.transform(type, action)
		return this
	}

	/**
	 * 设置模型是否应用环境光遮蔽。
	 */
	fun ambientOcclusion(ambientOcclusion: Boolean): RegistrateLegacyBlockModelBuilder {
		template.ambientOcclusion(ambientOcclusion)
		return this
	}

	/**
	 * 设置模型在界面中的光照方向。
	 *
	 * - [GuiLight.FRONT] 从正面照射，常用于物品。
	 * - [GuiLight.SIDE] 从侧面照射，常用于方块。
	 */
	fun guiLight(light: GuiLight): RegistrateLegacyBlockModelBuilder {
		template.guiLight(light)
		return this
	}

	/**
	 * 使用自定义加载器解析模型元素。
	 *
	 * @param customLoaderFactory 创建自定义加载器的工厂
	 * @param action 用于配置该加载器的回调
	 * @return 当前构建器
	 */
	fun <L : CustomLoaderBuilder> customLoader(customLoaderFactory: Supplier<L>, action: Consumer<L>): RegistrateLegacyBlockModelBuilder {
		template.customLoader(customLoaderFactory, action)
		return this
	}

	/**
	 * 配置根变换。它先于物品展示变换和方块状态中指定的旋转应用。
	 */
	fun rootTransforms(action: Consumer<RootTransformsBuilder>): RegistrateLegacyBlockModelBuilder {
		template.rootTransforms(action)
		return this
	}
}
