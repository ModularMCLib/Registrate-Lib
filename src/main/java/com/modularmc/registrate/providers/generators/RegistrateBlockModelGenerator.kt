package com.modularmc.registrate.providers.generators

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.ProviderType

import net.minecraft.client.data.models.BlockModelGenerators
import net.minecraft.client.data.models.ItemModelOutput
import net.minecraft.client.data.models.MultiVariant
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator
import net.minecraft.client.data.models.blockstates.MultiPartGenerator
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator
import net.minecraft.client.data.models.blockstates.PropertyDispatch
import net.minecraft.client.data.models.model.*
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher
import net.minecraft.client.renderer.block.dispatch.SingleVariant
import net.minecraft.client.renderer.block.dispatch.VariantMutator
import net.minecraft.client.resources.model.sprite.Material
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap

import java.util.Optional
import java.util.function.BiConsumer
import java.util.function.Consumer

open class RegistrateBlockModelGenerator private constructor(
	private val parent: AbstractRegistrate<*>,
	known: Consumer<BlockModelDefinitionGenerator>,
	item: ItemModelOutput,
	model: BiConsumer<Identifier, ModelInstance>,
	val seenBlockstates: MutableMap<Block, BlockStateModelDispatcher>,
) : BlockModelGenerators({ generator ->
	seenBlockstates[generator.block()] = generator.create()
	known.accept(generator)
}, item, model) {
	constructor(
		parent: AbstractRegistrate<*>,
		blockstates: Consumer<BlockModelDefinitionGenerator>,
		items: ItemModelOutput,
		models: BiConsumer<Identifier, ModelInstance>,
	) : this(parent, blockstates, items, models, Reference2ObjectOpenHashMap())

	fun getSingleVariantModel(block: Block): Optional<Identifier> {
		val selector = seenBlockstates[block]?.simpleModels()?.orElse(null)
		val variant = selector?.models()?.get("") as? SingleVariant.Unbaked
		return Optional.ofNullable(variant?.variant()?.modelLocation())
	}

	override fun run() {
		parent.genData(ProviderType.BLOCKSTATE, this)
	}

	fun create(block: Block, model: Identifier) {
		this.blockStateOutput.accept(createSimpleBlock(block, plainVariant(model)))
	}

	fun mcLoc(id: String): Identifier = Identifier.withDefaultNamespace(id)

	fun modLoc(id: String): Identifier = Identifier.fromNamespaceAndPath(parent.modid, id)

	fun mcBlockTexture(path: String): Material = Material(mcLoc("block/$path"))

	fun modBlockTexture(path: String): Material = Material(modLoc("block/$path"))

	@JvmOverloads
	fun withBuilder(template: ExtendedModelTemplateBuilder, texture: TextureMapping = TextureMapping()): RegistrateLegacyBlockModelBuilder =
		RegistrateLegacyBlockModelBuilder(modelOutput, template, texture)

	val builder: RegistrateLegacyBlockModelBuilder
		get() = withBuilder(ExtendedModelTemplateBuilder())

	fun withParent(template: ModelTemplate): RegistrateLegacyBlockModelBuilder = withBuilder(ExtendedModelTemplateBuilder.of(template))

	fun withParent(template: ModelTemplate, texture: TextureMapping): RegistrateLegacyBlockModelBuilder = withBuilder(ExtendedModelTemplateBuilder.of(template), texture)

	fun withParent(model: TexturedModel): RegistrateLegacyBlockModelBuilder = withBuilder(ExtendedModelTemplateBuilder.of(model.template), model.mapping)

	fun blockTexture(block: Block): Material = TextureMapping.getBlockTexture(block)

	fun blockTexture(block: Block, suffix: String): Material = TextureMapping.getBlockTexture(block, suffix)

	fun generateWithTemplate(block: Block, template: ModelTemplate, textures: TextureMapping) {
		create(block, template.create(block, textures, modelOutput))
	}

	fun generate(block: Block, texture: TexturedModel.Provider) {
		blockStateOutput.accept(createSimpleBlock(block, plainVariant(texture.create(block, modelOutput))))
	}

	fun generateLogBlock(block: RotatedPillarBlock) {
		generateAxisBlock(block, blockTexture(block), blockTexture(block, "_top"))
	}

	@JvmOverloads
	fun generateAxisBlock(block: RotatedPillarBlock, side: Material = blockTexture(block, "_side"), end: Material = blockTexture(block, "_end")) {
		generateAxisBlockInternal(block, side, end, ModelTemplates.CUBE_COLUMN, ModelTemplates.CUBE_COLUMN_HORIZONTAL)
	}

	private fun generateAxisBlockInternal(block: RotatedPillarBlock, side: Material, end: Material, cubeColumn: ModelTemplate, cubeColumnHorizontal: ModelTemplate) {
		generateAxisBlock(
			block,
			plainVariant(cubeColumn.create(block, TextureMapping.column(side, end), modelOutput)),
			plainVariant(cubeColumnHorizontal.create(block, TextureMapping.column(side, end), modelOutput)),
		)
	}

	fun generateAxisBlock(block: RotatedPillarBlock, vertical: MultiVariant, horizontal: MultiVariant) {
		blockStateOutput.accept(createRotatedPillarWithHorizontalVariant(block, vertical, horizontal))
	}

	fun generateHorizontalBlock(block: Block, side: Material, front: Material, top: Material) {
		val mapping =
			TextureMapping().put(TextureSlot.SIDE, side).put(TextureSlot.FRONT, front).put(TextureSlot.TOP, top)
		generateHorizontalBlock(block, plainVariant(ModelTemplates.CUBE_ORIENTABLE.create(block, mapping, modelOutput)))
	}

	fun generateHorizontalBlock(block: Block, model: MultiVariant) {
		blockStateOutput.accept(MultiVariantGenerator.dispatch(block, model).with(ROTATION_HORIZONTAL_FACING))
	}

	fun generateHorizontalBlock(block: Block, multiVariant: MultiVariant, angleOffset: Int) {
		blockStateOutput.accept(
			MultiVariantGenerator.dispatch(block, multiVariant).with(rotationHorizontalFacing(angleOffset)),
		)
	}

	fun generateHorizontalFaceBlock(block: Block, model: MultiVariant) {
		generateHorizontalFaceBlock(block, model, DEFAULT_ANGLE_OFFSET)
	}

	fun generateHorizontalFaceBlock(block: Block, variant: MultiVariant, angleOffset: Int) {
		blockStateOutput.accept(
			MultiVariantGenerator.dispatch(block, variant).with(rotationAttachFaceAndHorizontalFacing(angleOffset)),
		)
	}

	@JvmOverloads
	fun generateDirectionalBlock(block: Block, model: MultiVariant, angleOffsetY: Int = DEFAULT_ANGLE_OFFSET) {
		blockStateOutput.accept(MultiVariantGenerator.dispatch(block, model).with(rotationFacing(angleOffsetY)))
	}

	fun generateStairsBlock(block: StairBlock, texture: Material) {
		generateStairsBlock(block, texture, texture, texture)
	}

	fun generateStairsBlock(block: StairBlock, name: String, texture: Material) {
		generateStairsBlock(block, name, texture, texture, texture)
	}

	fun generateStairsBlock(block: StairBlock, side: Material, bottom: Material, top: Material) {
		val textures: TextureMapping = sideBottomTopTextures(side, bottom, top)
		blockStateOutput.accept(
			createStairs(
				block,
				plainVariant(ModelTemplates.STAIRS_INNER.create(block, textures, modelOutput)),
				plainVariant(ModelTemplates.STAIRS_STRAIGHT.create(block, textures, modelOutput)),
				plainVariant(ModelTemplates.STAIRS_OUTER.create(block, textures, modelOutput)),
			),
		)
	}

	fun generateStairsBlock(block: StairBlock, name: String, side: Material, bottom: Material, top: Material) {
		generateStairsBlockInternal(
			block,
			modLoc(name + "_stairs"),
			side,
			bottom,
			top,
			ModelTemplates.STAIRS_STRAIGHT,
			ModelTemplates.STAIRS_INNER,
			ModelTemplates.STAIRS_OUTER,
		)
	}

	private fun generateStairsBlockInternal(
		block: StairBlock,
		baseName: Identifier,
		side: Material,
		bottom: Material,
		top: Material,
		straightTemplate: ModelTemplate,
		innerTemplate: ModelTemplate,
		outerTemplate: ModelTemplate,
	) {
		val modelLocation = baseName.withPrefix("block/")
		val textures: TextureMapping = sideBottomTopTextures(side, bottom, top)
		val straightVariant = plainVariant(straightTemplate.create(modelLocation, textures, modelOutput))
		val innerVariant = plainVariant(innerTemplate.create(modelLocation.withSuffix("_inner"), textures, modelOutput))
		val outerVariant = plainVariant(outerTemplate.create(modelLocation.withSuffix("_outer"), textures, modelOutput))
		blockStateOutput.accept(createStairs(block, innerVariant, straightVariant, outerVariant))
	}

	fun generateStairsBlock(block: StairBlock, stairs: MultiVariant, stairsInner: MultiVariant, stairsOuter: MultiVariant) {
		blockStateOutput.accept(createStairs(block, stairsInner, stairs, stairsOuter))
	}

	fun generateSlabBlock(block: SlabBlock, doubleSlab: MultiVariant, texture: Material) {
		generateSlabBlock(block, doubleSlab, texture, texture, texture)
	}

	fun generateSlabBlock(block: SlabBlock, doubleSlab: MultiVariant, side: Material, bottom: Material, top: Material) {
		val textures: TextureMapping = sideBottomTopTextures(side, bottom, top)
		val slabTop = plainVariant(ModelTemplates.SLAB_TOP.create(block, textures, modelOutput))
		val slabBottom = plainVariant(ModelTemplates.SLAB_BOTTOM.create(block, textures, modelOutput))
		generateSlabBlock(block, slabBottom, slabTop, doubleSlab)
	}

	fun generateSlabBlock(block: SlabBlock, bottom: MultiVariant, top: MultiVariant, doubleslab: MultiVariant) {
		blockStateOutput.accept(createSlab(block, bottom, top, doubleslab))
	}

	fun generateButtonBlock(block: ButtonBlock, texture: Material) {
		val textures = TextureMapping.defaultTexture(texture)
		val button = plainVariant(ModelTemplates.BUTTON.create(block, textures, modelOutput))
		val buttonPressed = plainVariant(ModelTemplates.BUTTON_PRESSED.create(block, textures, modelOutput))
		generateButtonBlock(block, button, buttonPressed)
	}

	fun generateButtonBlock(block: ButtonBlock, button: MultiVariant, buttonPressed: MultiVariant) {
		blockStateOutput.accept(createButton(block, button, buttonPressed))
	}

	fun generatePressurePlateBlock(block: PressurePlateBlock, texture: Material) {
		val textures = TextureMapping.defaultTexture(texture)
		val pressurePlate = plainVariant(ModelTemplates.PRESSURE_PLATE_UP.create(block, textures, modelOutput))
		val pressurePlateDown = plainVariant(ModelTemplates.PRESSURE_PLATE_DOWN.create(block, textures, modelOutput))
		generatePressurePlateBlock(block, pressurePlate, pressurePlateDown)
	}

	fun generatePressurePlateBlock(block: PressurePlateBlock, pressurePlate: MultiVariant, pressurePlateDown: MultiVariant) {
		blockStateOutput.accept(createPressurePlate(block, pressurePlate, pressurePlateDown))
	}

	fun generateSignBlock(signBlock: StandingSignBlock, wallSignBlock: WallSignBlock, texture: Material) {
		val sign =
			plainVariant(ModelTemplates.PARTICLE_ONLY.create(signBlock, TextureMapping.particle(texture), modelOutput))
		generateSignBlock(signBlock, wallSignBlock, sign)
	}

	fun generateSignBlock(signBlock: StandingSignBlock, wallSignBlock: WallSignBlock, sign: MultiVariant) {
		blockStateOutput.accept(createSimpleBlock(signBlock, sign))
		blockStateOutput.accept(createSimpleBlock(wallSignBlock, sign))
	}

	fun generateHangingSignBlock(hangingSignBlock: CeilingHangingSignBlock, wallHangingSignBlock: WallHangingSignBlock, texture: Material) {
		val hangingSign = plainVariant(
			ModelTemplates.PARTICLE_ONLY.create(
				hangingSignBlock,
				TextureMapping.particle(texture),
				modelOutput,
			),
		)
		generateHangingSignBlock(hangingSignBlock, wallHangingSignBlock, hangingSign)
	}

	fun generateHangingSignBlock(hangingSignBlock: CeilingHangingSignBlock, wallHangingSignBlock: WallHangingSignBlock, hangingSign: MultiVariant) {
		blockStateOutput.accept(createSimpleBlock(hangingSignBlock, hangingSign))
		blockStateOutput.accept(createSimpleBlock(wallHangingSignBlock, hangingSign))
	}

	fun generateFourWayBlock(block: CrossCollisionBlock, post: MultiVariant, side: MultiVariant) {
		val builder = MultiPartGenerator.multiPart(block).with(post)
		generateFourWayMultipart(builder, side)
	}

	fun generateFourWayMultipart(builder: MultiPartGenerator, side: MultiVariant) {
		PipeBlock.PROPERTY_BY_DIRECTION.forEach { (direction: Direction?, property: BooleanProperty?) ->
			if (direction!!.getAxis().isHorizontal()) {
				val yRot = when (direction) {
					Direction.NORTH -> NOP
					Direction.EAST -> Y_ROT_90
					Direction.SOUTH -> Y_ROT_180
					Direction.WEST -> Y_ROT_270
					else -> throw IllegalStateException()
				}
				builder.with(condition().term<Boolean>(property!!, true), side.with(UV_LOCK.then(yRot)))
			}
		}
		blockStateOutput.accept(builder)
	}

	fun generateFenceBlock(block: FenceBlock, texture: Material) {
		generateFenceBlockInternal(block, texture, ModelTemplates.FENCE_POST, ModelTemplates.FENCE_SIDE)
	}

	fun generateFenceBlock(block: FenceBlock, name: String, texture: Material) {
		generateFenceBlockInternal(block, name, texture, ModelTemplates.FENCE_POST, ModelTemplates.FENCE_SIDE)
	}

	private fun generateFenceBlockInternal(block: FenceBlock, texture: Material, fencePostTemplate: ModelTemplate, fenceSideTemplate: ModelTemplate) {
		val textures = TextureMapping.defaultTexture(texture)
		generateFourWayBlock(
			block,
			plainVariant(fencePostTemplate.create(block, textures, modelOutput)),
			plainVariant(fenceSideTemplate.create(block, textures, modelOutput)),
		)
	}

	private fun generateFenceBlockInternal(block: FenceBlock, name: String, texture: Material, fencePostTemplate: ModelTemplate, fenceSideTemplate: ModelTemplate) {
		val textures = TextureMapping.defaultTexture(texture)
		generateFourWayBlock(
			block,
			plainVariant(fencePostTemplate.create(modLoc("block/" + name + "_fence_post"), textures, modelOutput)),
			plainVariant(fenceSideTemplate.create(modLoc("block/" + name + "_fence_side"), textures, modelOutput)),
		)
	}

	fun generateFenceGateBlock(block: FenceGateBlock, texture: Material) {
		val textures = TextureMapping.defaultTexture(texture)
		generateFenceGateBlock(
			block,
			plainVariant(ModelTemplates.FENCE_GATE_CLOSED.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.FENCE_GATE_OPEN.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.FENCE_GATE_WALL_CLOSED.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.FENCE_GATE_WALL_OPEN.create(block, textures, modelOutput)),
		)
	}

	fun generateFenceGateBlock(block: FenceGateBlock, name: String, texture: Material) {
		generateFenceGateBlockInternal(
			block,
			modLoc(name + "_fence_gate"),
			texture,
			ModelTemplates.FENCE_GATE_CLOSED,
			ModelTemplates.FENCE_GATE_OPEN,
			ModelTemplates.FENCE_GATE_WALL_CLOSED,
			ModelTemplates.FENCE_GATE_WALL_OPEN,
		)
	}

	private fun generateFenceGateBlockInternal(
		block: FenceGateBlock,
		baseName: Identifier,
		texture: Material,
		closedTemplate: ModelTemplate,
		openTemplate: ModelTemplate,
		wallClosedTemplate: ModelTemplate,
		wallOpenTemplate: ModelTemplate,
	) {
		val baseModel = baseName.withPrefix("block/")
		val textures = TextureMapping.defaultTexture(texture)
		val gate = plainVariant(closedTemplate.create(baseModel, textures, modelOutput))
		val gateOpen = plainVariant(openTemplate.create(baseModel.withSuffix("_open"), textures, modelOutput))
		val gateWall = plainVariant(wallClosedTemplate.create(baseModel.withSuffix("_wall"), textures, modelOutput))
		val gateWallOpen =
			plainVariant(wallOpenTemplate.create(baseModel.withSuffix("_wall_open"), textures, modelOutput))
		generateFenceGateBlock(block, gate, gateOpen, gateWall, gateWallOpen)
	}

	fun generateFenceGateBlock(block: FenceGateBlock, gate: MultiVariant, gateOpen: MultiVariant, gateWall: MultiVariant, gateWallOpen: MultiVariant) {
		blockStateOutput.accept(createFenceGate(block, gateOpen, gate, gateWallOpen, gateWall, true))
	}

	fun generateWallBlock(block: WallBlock, texture: Material) {
		val textures = TextureMapping.singleSlot(TextureSlot.WALL, texture)
		blockStateOutput.accept(
			createWall(
				block,
				plainVariant(ModelTemplates.WALL_POST.create(block, textures, modelOutput)),
				plainVariant(ModelTemplates.WALL_LOW_SIDE.create(block, textures, modelOutput)),
				plainVariant(ModelTemplates.WALL_TALL_SIDE.create(block, textures, modelOutput)),
			),
		)
	}

	fun generateWallBlock(block: WallBlock, name: String, texture: Material) {
		val baseName = modLoc(name + "_wall")
		generateWallBlockInternal(
			block,
			baseName,
			texture,
			ModelTemplates.WALL_POST,
			ModelTemplates.WALL_LOW_SIDE,
			ModelTemplates.WALL_TALL_SIDE,
		)
	}

	private fun generateWallBlockInternal(block: WallBlock, baseName: Identifier, texture: Material, postTemplate: ModelTemplate, sideTemplate: ModelTemplate, tallSideTemplate: ModelTemplate) {
		val textures = TextureMapping.singleSlot(TextureSlot.WALL, texture)
		val baseModel = baseName.withPrefix("block/")
		generateWallBlock(
			block,
			plainVariant(postTemplate.create(baseModel.withSuffix("_post"), textures, modelOutput)),
			plainVariant(sideTemplate.create(baseModel.withSuffix("_side"), textures, modelOutput)),
			plainVariant(tallSideTemplate.create(baseModel.withSuffix("_side_tall"), textures, modelOutput)),
		)
	}

	fun generateWallBlock(block: WallBlock, post: MultiVariant, side: MultiVariant, sideTall: MultiVariant) {
		blockStateOutput.accept(createWall(block, post, side, sideTall))
	}

	fun generatePaneBlock(block: IronBarsBlock, pane: Material, edge: Material) {
		val textures = TextureMapping().put(TextureSlot.PANE, pane).put(TextureSlot.EDGE, edge)
		generatePaneBlock(
			block,
			plainVariant(ModelTemplates.STAINED_GLASS_PANE_POST.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.STAINED_GLASS_PANE_SIDE.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.STAINED_GLASS_PANE_SIDE_ALT.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.STAINED_GLASS_PANE_NOSIDE.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.STAINED_GLASS_PANE_NOSIDE_ALT.create(block, textures, modelOutput)),
		)
	}

	fun generatePaneBlock(block: IronBarsBlock, name: String, pane: Material, edge: Material) {
		val baseName = modLoc(name + "_pane")
		generatePaneBlockInternal(
			block,
			baseName,
			pane,
			edge,
			ModelTemplates.STAINED_GLASS_PANE_POST,
			ModelTemplates.STAINED_GLASS_PANE_SIDE,
			ModelTemplates.STAINED_GLASS_PANE_SIDE_ALT,
			ModelTemplates.STAINED_GLASS_PANE_NOSIDE,
			ModelTemplates.STAINED_GLASS_PANE_NOSIDE_ALT,
		)
	}

	private fun generatePaneBlockInternal(
		block: IronBarsBlock,
		baseName: Identifier,
		pane: Material,
		edge: Material,
		postTemplate: ModelTemplate,
		sideTemplate: ModelTemplate,
		sideAltTemplate: ModelTemplate,
		noSideTemplate: ModelTemplate,
		noSideAltTemplate: ModelTemplate,
	) {
		val baseModel = baseName.withPrefix("block/")
		val textures = TextureMapping().put(TextureSlot.PANE, pane).put(TextureSlot.EDGE, edge)
		generatePaneBlock(
			block,
			plainVariant(postTemplate.create(baseModel.withSuffix("_post"), textures, modelOutput)),
			plainVariant(sideTemplate.create(baseModel.withSuffix("_side"), textures, modelOutput)),
			plainVariant(sideAltTemplate.create(baseModel.withSuffix("_side_alt"), textures, modelOutput)),
			plainVariant(noSideTemplate.create(baseModel.withSuffix("_noside"), textures, modelOutput)),
			plainVariant(noSideAltTemplate.create(baseModel.withSuffix("_noside_alt"), textures, modelOutput)),
		)
	}

	fun generatePaneBlock(block: IronBarsBlock, post: MultiVariant, side: MultiVariant, sideAlt: MultiVariant, noSide: MultiVariant, noSideAlt: MultiVariant) {
		blockStateOutput.accept(
			MultiPartGenerator.multiPart(block)
				.with(post)
				.with(condition().term<Boolean>(BlockStateProperties.NORTH, true), side)
				.with(condition().term<Boolean>(BlockStateProperties.EAST, true), side.with(Y_ROT_90))
				.with(condition().term<Boolean>(BlockStateProperties.SOUTH, true), sideAlt)
				.with(condition().term<Boolean>(BlockStateProperties.WEST, true), sideAlt.with(Y_ROT_90))
				.with(condition().term<Boolean>(BlockStateProperties.NORTH, false), noSide)
				.with(condition().term<Boolean>(BlockStateProperties.EAST, false), noSideAlt)
				.with(condition().term<Boolean>(BlockStateProperties.SOUTH, false), noSideAlt.with(Y_ROT_90))
				.with(condition().term<Boolean>(BlockStateProperties.WEST, false), noSide.with(Y_ROT_270)),
		)
	}

	fun generateDoorBlock(block: DoorBlock, bottom: Material, top: Material) {
		val textures = TextureMapping.door(top, bottom)
		generateDoorBlock(
			block,
			plainVariant(ModelTemplates.DOOR_BOTTOM_LEFT.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.DOOR_BOTTOM_LEFT_OPEN.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.DOOR_BOTTOM_RIGHT.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.DOOR_BOTTOM_RIGHT_OPEN.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.DOOR_TOP_LEFT.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.DOOR_TOP_LEFT_OPEN.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.DOOR_TOP_RIGHT.create(block, textures, modelOutput)),
			plainVariant(ModelTemplates.DOOR_TOP_RIGHT_OPEN.create(block, textures, modelOutput)),
		)
	}

	fun generateDoorBlock(block: DoorBlock, name: String, bottom: Material, top: Material) {
		generateDoorBlockInternal(block, modLoc(name + "_door"), bottom, top)
	}

	private fun generateDoorBlockInternal(
		block: DoorBlock,
		baseName: Identifier,
		bottom: Material,
		top: Material,
		bottomLeftTemplate: ModelTemplate = ModelTemplates.DOOR_BOTTOM_LEFT,
		bottomLeftOpenTemplate: ModelTemplate = ModelTemplates.DOOR_BOTTOM_LEFT_OPEN,
		bottomRightTemplate: ModelTemplate = ModelTemplates.DOOR_BOTTOM_RIGHT,
		bottomRightOpenTemplate: ModelTemplate = ModelTemplates.DOOR_BOTTOM_RIGHT_OPEN,
		topLeftTemplate: ModelTemplate = ModelTemplates.DOOR_TOP_LEFT,
		topLeftOpenTemplate: ModelTemplate = ModelTemplates.DOOR_TOP_LEFT_OPEN,
		topRightTemplate: ModelTemplate = ModelTemplates.DOOR_TOP_RIGHT,
		topRightOpenTemplate: ModelTemplate = ModelTemplates.DOOR_TOP_RIGHT_OPEN,
	) {
		val baseModel = baseName.withPrefix("block/")
		val textures = TextureMapping.door(top, bottom)
		val bottomLeft =
			plainVariant(bottomLeftTemplate.create(baseModel.withSuffix("_bottom_left"), textures, modelOutput))
		val bottomLeftOpen = plainVariant(
			bottomLeftOpenTemplate.create(
				baseModel.withSuffix("_bottom_left_open"),
				textures,
				modelOutput,
			),
		)
		val bottomRight =
			plainVariant(bottomRightTemplate.create(baseModel.withSuffix("_bottom_right"), textures, modelOutput))
		val bottomRightOpen = plainVariant(
			bottomRightOpenTemplate.create(
				baseModel.withSuffix("_bottom_right_open"),
				textures,
				modelOutput,
			),
		)
		val topLeft = plainVariant(topLeftTemplate.create(baseModel.withSuffix("_top_left"), textures, modelOutput))
		val topLeftOpen =
			plainVariant(topLeftOpenTemplate.create(baseModel.withSuffix("_top_left_open"), textures, modelOutput))
		val topRight = plainVariant(topRightTemplate.create(baseModel.withSuffix("_top_right"), textures, modelOutput))
		val topRightOpen =
			plainVariant(topRightOpenTemplate.create(baseModel.withSuffix("_top_right_open"), textures, modelOutput))
		generateDoorBlock(
			block,
			bottomLeft,
			bottomLeftOpen,
			bottomRight,
			bottomRightOpen,
			topLeft,
			topLeftOpen,
			topRight,
			topRightOpen,
		)
	}

	fun generateDoorBlock(
		block: DoorBlock,
		bottomLeft: MultiVariant,
		bottomLeftOpen: MultiVariant,
		bottomRight: MultiVariant,
		bottomRightOpen: MultiVariant,
		topLeft: MultiVariant,
		topLeftOpen: MultiVariant,
		topRight: MultiVariant,
		topRightOpen: MultiVariant,
	) {
		blockStateOutput.accept(
			createDoor(
				block,
				bottomLeft,
				bottomLeftOpen,
				bottomRight,
				bottomRightOpen,
				topLeft,
				topLeftOpen,
				topRight,
				topRightOpen,
			),
		)
	}

	fun generateTrapdoorBlock(block: TrapDoorBlock, texture: Material, orientable: Boolean) {
		val textures = TextureMapping.defaultTexture(texture)
		generateTrapdoorBlock(
			block,
			plainVariant(
				(if (orientable) ModelTemplates.ORIENTABLE_TRAPDOOR_BOTTOM else ModelTemplates.TRAPDOOR_BOTTOM).create(
					block,
					textures,
					modelOutput,
				),
			),
			plainVariant(
				(if (orientable) ModelTemplates.ORIENTABLE_TRAPDOOR_TOP else ModelTemplates.TRAPDOOR_TOP).create(
					block,
					textures,
					modelOutput,
				),
			),
			plainVariant(
				(if (orientable) ModelTemplates.ORIENTABLE_TRAPDOOR_OPEN else ModelTemplates.TRAPDOOR_OPEN).create(
					block,
					textures,
					modelOutput,
				),
			),
			orientable,
		)
	}

	fun generateTrapdoorBlock(block: TrapDoorBlock, name: String, texture: Material, orientable: Boolean) {
		generateTrapdoorBlockInternal(block, modLoc(name + "_trapdoor"), texture, orientable)
	}

	private fun generateTrapdoorBlockInternal(
		block: TrapDoorBlock,
		baseName: Identifier,
		texture: Material,
		orientable: Boolean,
		bottomTemplate: ModelTemplate = if (orientable) ModelTemplates.ORIENTABLE_TRAPDOOR_BOTTOM else ModelTemplates.TRAPDOOR_BOTTOM,
		topTemplate: ModelTemplate = if (orientable) ModelTemplates.ORIENTABLE_TRAPDOOR_TOP else ModelTemplates.TRAPDOOR_TOP,
		openTemplate: ModelTemplate = if (orientable) ModelTemplates.ORIENTABLE_TRAPDOOR_OPEN else ModelTemplates.TRAPDOOR_OPEN,
	) {
		val baseModel = baseName.withPrefix("block/")
		val textures = TextureMapping.defaultTexture(texture)
		val bottom = plainVariant(bottomTemplate.create(baseModel.withSuffix("_bottom"), textures, modelOutput))
		val top = plainVariant(topTemplate.create(baseModel.withSuffix("_top"), textures, modelOutput))
		val open = plainVariant(openTemplate.create(baseModel.withSuffix("_open"), textures, modelOutput))
		generateTrapdoorBlock(block, bottom, top, open, orientable)
	}

	fun generateTrapdoorBlock(block: TrapDoorBlock, bottom: MultiVariant, top: MultiVariant, open: MultiVariant, orientable: Boolean) {
		blockStateOutput.accept(
			if (orientable) {
				createOrientableTrapdoor(block, top, bottom, open)
			} else {
				createTrapdoor(
					block,
					top,
					bottom,
					open,
				)
			},
		)
	}

	companion object {
		private fun sideBottomTopTextures(side: Material, bottom: Material, top: Material): TextureMapping = TextureMapping()
			.put(TextureSlot.SIDE, side)
			.put(TextureSlot.BOTTOM, bottom)
			.put(TextureSlot.TOP, top)

		private const val DEFAULT_ANGLE_OFFSET = 180

		private fun yRot(direction: Direction, angleOffset: Int): VariantMutator = when ((direction.get2DDataValue() + angleOffset / 90) % 4) {
			0 -> NOP
			1 -> Y_ROT_90
			2 -> Y_ROT_180
			3 -> Y_ROT_270
			else -> throw IllegalStateException()
		}

		fun rotationHorizontalFacing(angleOffset: Int): PropertyDispatch<VariantMutator> = PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
			.generate { direction -> yRot(direction, angleOffset) }

		fun rotationFacing(angleOffsetY: Int): PropertyDispatch<VariantMutator> = PropertyDispatch.modify(BlockStateProperties.FACING)
			.generate { direction ->
				val xRot = when (direction) {
					Direction.DOWN -> X_ROT_180
					Direction.UP -> NOP
					Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST -> X_ROT_90
				}
				val yRot =
					if (direction.axis.isVertical) NOP else yRot(direction, angleOffsetY)
				xRot.then(yRot)
			}

		fun rotationAttachFaceAndHorizontalFacing(angleOffsetY: Int): PropertyDispatch<VariantMutator> = PropertyDispatch.modify(
			BlockStateProperties.ATTACH_FACE,
			BlockStateProperties.HORIZONTAL_FACING,
		).generate { attachFace, direction ->
			val xRot = when (attachFace) {
				AttachFace.FLOOR -> NOP
				AttachFace.WALL -> X_ROT_90
				AttachFace.CEILING -> X_ROT_180
			}
			val yRot = yRot(
				direction,
				if (attachFace == AttachFace.CEILING) angleOffsetY + 180 else angleOffsetY,
			)
			xRot.then(yRot)
		}
	}
}
