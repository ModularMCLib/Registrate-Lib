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

class RegistrateLegacyBlockModelBuilder internal constructor(private val output: BiConsumer<Identifier, ModelInstance>, private val template: ExtendedModelTemplateBuilder, texture: TextureMapping) {
	private val texture: TextureMapping = texture.copy()

	fun texture(slot: TextureSlot, texture: Material): RegistrateLegacyBlockModelBuilder {
		this.template.requiredTextureSlot(slot)
		this.texture.put(slot, texture)
		return this
	}

	fun transformTemplate(action: Consumer<ExtendedModelTemplateBuilder>): RegistrateLegacyBlockModelBuilder {
		action.accept(template)
		return this
	}

	fun transformTexture(action: Consumer<TextureMapping>): RegistrateLegacyBlockModelBuilder {
		action.accept(texture)
		return this
	}

	fun build(block: Block): Identifier = template.build().create(block, texture, output)

	fun build(loc: Identifier): Identifier = template.build().create(loc, texture, output)

	// Delegated methods from Template Builder

	/**
	 * Parent model which this template will inherit its properties from.
	 */
	fun parent(parent: Identifier): RegistrateLegacyBlockModelBuilder {
		template.parent(parent)
		return this
	}

	/**
	 * Suffix appended onto the models file path.
	 */
	fun suffix(suffix: String): RegistrateLegacyBlockModelBuilder {
		template.suffix(suffix)
		return this
	}

	/**
	 * Begin building a new transform for the given perspective.
	 *
	 * @param type the perspective to create or return the builder for
	 * @return the builder for the given perspective
	 * @throws NullPointerException if `type` is `null`
	 */
	fun transform(type: ItemDisplayContext, action: Consumer<TransformVecBuilder>): RegistrateLegacyBlockModelBuilder {
		template.transform(type, action)
		return this
	}

	/**
	 * Sets whether or not this model should apply ambient occlusion.
	 */
	fun ambientOcclusion(ambientOcclusion: Boolean): RegistrateLegacyBlockModelBuilder {
		template.ambientOcclusion(ambientOcclusion)
		return this
	}

	/**
	 * Sets the gui light style for this model.
	 *
	 *
	 *  * [net.minecraft.client.resources.model.UnbakedModel.GuiLight.FRONT] for head on light, commonly used for items.
	 *  * [net.minecraft.client.resources.model.UnbakedModel.GuiLight.SIDE] for the model to be side lit, commonly used for blocks.
	 *
	 */
	fun guiLight(light: GuiLight): RegistrateLegacyBlockModelBuilder {
		template.guiLight(light)
		return this
	}

	/**
	 * Use a custom loader instead of the vanilla elements.
	 *
	 * @param customLoaderFactory function that returns the custom loader to set, given this
	 * @return the custom loader builder
	 */
	fun <L : CustomLoaderBuilder> customLoader(customLoaderFactory: Supplier<L>, action: Consumer<L>): RegistrateLegacyBlockModelBuilder {
		template.customLoader(customLoaderFactory, action)
		return this
	}

	/**
	 * Modifies the transformation applied right before item display transformations and rotations specified in block
	 * states.
	 */
	fun rootTransforms(action: Consumer<RootTransformsBuilder>): RegistrateLegacyBlockModelBuilder {
		template.rootTransforms(action)
		return this
	}
}
