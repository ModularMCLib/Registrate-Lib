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

/** 为已注册方块生成模型与状态定义，并记录状态使用的模型供物品生成阶段查询。 */
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
	/** 使用本轮方块状态、物品和模型输出创建生成器。写出的状态会同时进入内部状态索引。 */
	constructor(
		parent: AbstractRegistrate<*>,
		blockstates: Consumer<BlockModelDefinitionGenerator>,
		items: ItemModelOutput,
		models: BiConsumer<Identifier, ModelInstance>,
	) : this(parent, blockstates, items, models, Reference2ObjectOpenHashMap())

	/** 仅当方块已生成单一默认变体且它引用一个模型时，返回对应模型的标识。 */
	fun getSingleVariantModel(block: Block): Optional<Identifier> {
		val selector = seenBlockstates[block]?.simpleModels()?.orElse(null)
		val variant = selector?.models()?.get("") as? SingleVariant.Unbaked
		return Optional.ofNullable(variant?.variant()?.modelLocation())
	}

	/** 执行所属模组登记的方块状态数据生成回调。 */
	override fun run() {
		parent.genData(ProviderType.BLOCKSTATE, this)
	}

	/** 为当前方块生成只引用一个既有模型的状态定义。 */
	fun create(block: Block, model: Identifier) {
		this.blockStateOutput.accept(createSimpleBlock(block, plainVariant(model)))
	}

	/** 使用 Minecraft 命名空间和给定路径构造资源标识。 */
	fun mcLoc(id: String): Identifier = Identifier.withDefaultNamespace(id)

	/** 使用当前模组命名空间和给定路径构造资源标识。 */
	fun modLoc(id: String): Identifier = Identifier.fromNamespaceAndPath(parent.modid, id)

	/** 引用 Minecraft 命名空间下 `textures/block` 中的纹理。[path] 不含目录前缀和文件扩展名。 */
	fun mcBlockTexture(path: String): Material = Material(mcLoc("block/$path"))

	/** 引用当前模组 `textures/block` 中的纹理。[path] 不含目录前缀和文件扩展名。 */
	fun modBlockTexture(path: String): Material = Material(modLoc("block/$path"))

	/** 用指定模板构建器和纹理映射开始配置模型。省略纹理时使用新的空映射。 */
	@JvmOverloads
	fun withBuilder(template: ExtendedModelTemplateBuilder, texture: TextureMapping = TextureMapping()): RegistrateLegacyBlockModelBuilder =
		RegistrateLegacyBlockModelBuilder(modelOutput, template, texture)

	val builder: RegistrateLegacyBlockModelBuilder
		get() = withBuilder(ExtendedModelTemplateBuilder())

	/** 从原版模型模板开始配置模型，调用方可以继续绑定纹理槽。 */
	fun withParent(template: ModelTemplate): RegistrateLegacyBlockModelBuilder = withBuilder(ExtendedModelTemplateBuilder.of(template))

	/** 基于指定模板和已有纹理映射配置模型。 */
	fun withParent(template: ModelTemplate, texture: TextureMapping): RegistrateLegacyBlockModelBuilder = withBuilder(ExtendedModelTemplateBuilder.of(template), texture)

	/** 从已有的带纹理模型继承模板和纹理映射，随后可继续配置。 */
	fun withParent(model: TexturedModel): RegistrateLegacyBlockModelBuilder = withBuilder(ExtendedModelTemplateBuilder.of(model.template), model.mapping)

	/** 按已注册方块的标识构造默认纹理引用。 */
	fun blockTexture(block: Block): Material = TextureMapping.getBlockTexture(block)

	/** 在方块默认纹理路径末尾追加 [suffix]，供端面或侧面等变体使用。 */
	fun blockTexture(block: Block, suffix: String): Material = TextureMapping.getBlockTexture(block, suffix)

	/** 将纹理填入模板写出模型，并生成引用该模型的单变体方块状态。 */
	fun generateWithTemplate(block: Block, template: ModelTemplate, textures: TextureMapping) {
		create(block, template.create(block, textures, modelOutput))
	}

	/** 由 [texture] 为方块写出带纹理模型，再登记只引用该模型的方块状态。 */
	fun generate(block: Block, texture: TexturedModel.Provider) {
		blockStateOutput.accept(createSimpleBlock(block, plainVariant(texture.create(block, modelOutput))))
	}

	/** 生成原木的轴向模型。侧面使用方块默认纹理，端面使用 `_top` 纹理。 */
	fun generateLogBlock(block: RotatedPillarBlock) {
		generateAxisBlock(block, blockTexture(block), blockTexture(block, "_top"))
	}

	/** 分别以 [side] 和 [end] 填充轴向模型的侧面和端面，生成垂直及水平状态。 */
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

	/** 使用已有的垂直和水平模型变体生成轴向方块状态。 */
	fun generateAxisBlock(block: RotatedPillarBlock, vertical: MultiVariant, horizontal: MultiVariant) {
		blockStateOutput.accept(createRotatedPillarWithHorizontalVariant(block, vertical, horizontal))
	}

	/** 用侧面、正面和顶部纹理创建定向立方体模型，并为四个水平朝向生成状态。 */
	fun generateHorizontalBlock(block: Block, side: Material, front: Material, top: Material) {
		val mapping =
			TextureMapping().put(TextureSlot.SIDE, side).put(TextureSlot.FRONT, front).put(TextureSlot.TOP, top)
		generateHorizontalBlock(block, plainVariant(ModelTemplates.CUBE_ORIENTABLE.create(block, mapping, modelOutput)))
	}

	/** 按原版水平朝向旋转给定模型，方块应声明水平朝向属性。 */
	fun generateHorizontalBlock(block: Block, model: MultiVariant) {
		blockStateOutput.accept(MultiVariantGenerator.dispatch(block, model).with(ROTATION_HORIZONTAL_FACING))
	}

	/** 按方块的水平朝向旋转模型，并用 [angleOffset] 补偿模型默认朝向。 */
	fun generateHorizontalBlock(block: Block, multiVariant: MultiVariant, angleOffset: Int) {
		blockStateOutput.accept(
			MultiVariantGenerator.dispatch(block, multiVariant).with(rotationHorizontalFacing(angleOffset)),
		)
	}

	/** 按默认角度旋转已有模型，生成可安装在地面、墙面或天花板的方块状态。 */
	fun generateHorizontalFaceBlock(block: Block, model: MultiVariant) {
		generateHorizontalFaceBlock(block, model, DEFAULT_ANGLE_OFFSET)
	}

	/** 依据安装面和水平朝向共同旋转模型，[angleOffset] 用于调整模型的基准角度。 */
	fun generateHorizontalFaceBlock(block: Block, variant: MultiVariant, angleOffset: Int) {
		blockStateOutput.accept(
			MultiVariantGenerator.dispatch(block, variant).with(rotationAttachFaceAndHorizontalFacing(angleOffset)),
		)
	}

	/** 为使用六向朝向属性的方块生成状态。[angleOffsetY] 用于调整水平方向的旋转。 */
	@JvmOverloads
	fun generateDirectionalBlock(block: Block, model: MultiVariant, angleOffsetY: Int = DEFAULT_ANGLE_OFFSET) {
		blockStateOutput.accept(MultiVariantGenerator.dispatch(block, model).with(rotationFacing(angleOffsetY)))
	}

	/** 用同一纹理生成楼梯的侧面、底面和顶面。 */
	fun generateStairsBlock(block: StairBlock, texture: Material) {
		generateStairsBlock(block, texture, texture, texture)
	}

	/** 用同一纹理生成楼梯模型，并以 [name] 为模型路径的基础名称。 */
	fun generateStairsBlock(block: StairBlock, name: String, texture: Material) {
		generateStairsBlock(block, name, texture, texture, texture)
	}

	/** 分别使用侧面、底面和顶面纹理生成楼梯的直段、内角和外角模型。 */
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

	/** 用指定纹理生成楼梯的三种形态，并从 [name] 派生各模型路径。 */
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

	/** 使用已有的直段、内角和外角模型，按楼梯的朝向、半部和形状选择变体。 */
	fun generateStairsBlock(block: StairBlock, stairs: MultiVariant, stairsInner: MultiVariant, stairsOuter: MultiVariant) {
		blockStateOutput.accept(createStairs(block, stairsInner, stairs, stairsOuter))
	}

	/** 用同一纹理生成上、下半砖模型，双层状态使用 [doubleSlab]。 */
	fun generateSlabBlock(block: SlabBlock, doubleSlab: MultiVariant, texture: Material) {
		generateSlabBlock(block, doubleSlab, texture, texture, texture)
	}

	/** 用各面的纹理生成上、下半砖模型，双层状态使用 [doubleSlab]。 */
	fun generateSlabBlock(block: SlabBlock, doubleSlab: MultiVariant, side: Material, bottom: Material, top: Material) {
		val textures: TextureMapping = sideBottomTopTextures(side, bottom, top)
		val slabTop = plainVariant(ModelTemplates.SLAB_TOP.create(block, textures, modelOutput))
		val slabBottom = plainVariant(ModelTemplates.SLAB_BOTTOM.create(block, textures, modelOutput))
		generateSlabBlock(block, slabBottom, slabTop, doubleSlab)
	}

	/** 用给定模型分别生成下半、上半和双层半砖状态。 */
	fun generateSlabBlock(block: SlabBlock, bottom: MultiVariant, top: MultiVariant, doubleslab: MultiVariant) {
		blockStateOutput.accept(createSlab(block, bottom, top, doubleslab))
	}

	/** 用同一纹理生成按钮常态和按下模型，并按安装面与朝向生成状态。 */
	fun generateButtonBlock(block: ButtonBlock, texture: Material) {
		val textures = TextureMapping.defaultTexture(texture)
		val button = plainVariant(ModelTemplates.BUTTON.create(block, textures, modelOutput))
		val buttonPressed = plainVariant(ModelTemplates.BUTTON_PRESSED.create(block, textures, modelOutput))
		generateButtonBlock(block, button, buttonPressed)
	}

	/** 使用已有的常态及按下模型，生成不同安装面和朝向的按钮状态。 */
	fun generateButtonBlock(block: ButtonBlock, button: MultiVariant, buttonPressed: MultiVariant) {
		blockStateOutput.accept(createButton(block, button, buttonPressed))
	}

	/** 用同一纹理生成压力板抬起和按下的模型。 */
	fun generatePressurePlateBlock(block: PressurePlateBlock, texture: Material) {
		val textures = TextureMapping.defaultTexture(texture)
		val pressurePlate = plainVariant(ModelTemplates.PRESSURE_PLATE_UP.create(block, textures, modelOutput))
		val pressurePlateDown = plainVariant(ModelTemplates.PRESSURE_PLATE_DOWN.create(block, textures, modelOutput))
		generatePressurePlateBlock(block, pressurePlate, pressurePlateDown)
	}

	/** 按受压属性为压力板选择抬起或按下变体。 */
	fun generatePressurePlateBlock(block: PressurePlateBlock, pressurePlate: MultiVariant, pressurePlateDown: MultiVariant) {
		blockStateOutput.accept(createPressurePlate(block, pressurePlate, pressurePlateDown))
	}

	/** 用 [texture] 生成告示牌的粒子模型，立式和墙上告示牌共用该模型。 */
	fun generateSignBlock(signBlock: StandingSignBlock, wallSignBlock: WallSignBlock, texture: Material) {
		val sign =
			plainVariant(ModelTemplates.PARTICLE_ONLY.create(signBlock, TextureMapping.particle(texture), modelOutput))
		generateSignBlock(signBlock, wallSignBlock, sign)
	}

	/** 让立式和墙上告示牌共用已有的方块模型。 */
	fun generateSignBlock(signBlock: StandingSignBlock, wallSignBlock: WallSignBlock, sign: MultiVariant) {
		blockStateOutput.accept(createSimpleBlock(signBlock, sign))
		blockStateOutput.accept(createSimpleBlock(wallSignBlock, sign))
	}

	/** 用指定的粒子纹理为悬挂和墙上悬挂告示牌生成方块状态。 */
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

	/** 让悬挂和墙上悬挂告示牌共用已有的方块模型。 */
	fun generateHangingSignBlock(hangingSignBlock: CeilingHangingSignBlock, wallHangingSignBlock: WallHangingSignBlock, hangingSign: MultiVariant) {
		blockStateOutput.accept(createSimpleBlock(hangingSignBlock, hangingSign))
		blockStateOutput.accept(createSimpleBlock(wallHangingSignBlock, hangingSign))
	}

	/** 用柱体和连接部件生成四向连接方块，连接部件按各方向的连接属性显示。 */
	fun generateFourWayBlock(block: CrossCollisionBlock, post: MultiVariant, side: MultiVariant) {
		val builder = MultiPartGenerator.multiPart(block).with(post)
		generateFourWayMultipart(builder, side)
	}

	/** 将单个连接部件接入四个水平连接属性，按方向应用对应旋转。 */
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

	/** 用指定纹理生成栅栏柱和连接部件，并按四向连接状态组合。 */
	fun generateFenceBlock(block: FenceBlock, texture: Material) {
		generateFenceBlockInternal(block, texture, ModelTemplates.FENCE_POST, ModelTemplates.FENCE_SIDE)
	}

	/** 以 [name] 派生栅栏柱和连接部件的模型路径，生成栅栏状态。 */
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

	/** 用指定纹理生成栅栏门在开启、关闭及嵌入墙体时的四种模型。 */
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

	/** 以 [name] 为模型基础路径生成栅栏门的开启、关闭和嵌墙变体。 */
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

	/** 按门的朝向、开启状态和嵌墙状态选择预先提供的栅栏门模型。 */
	fun generateFenceGateBlock(block: FenceGateBlock, gate: MultiVariant, gateOpen: MultiVariant, gateWall: MultiVariant, gateWallOpen: MultiVariant) {
		blockStateOutput.accept(createFenceGate(block, gateOpen, gate, gateWallOpen, gateWall, true))
	}

	/** 用指定纹理生成墙柱、矮墙段和高墙段模型，并按连接属性组合。 */
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

	/** 用 [name] 派生墙柱和两种墙段的模型路径，生成墙的状态定义。 */
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

	/** 根据墙柱及四向墙段属性，组合已有的柱体、矮墙段和高墙段模型。 */
	fun generateWallBlock(block: WallBlock, post: MultiVariant, side: MultiVariant, sideTall: MultiVariant) {
		blockStateOutput.accept(createWall(block, post, side, sideTall))
	}

	/** 用片面与边缘纹理生成板状方块的柱体和连接模型。 */
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

	/** 以 [name] 为模型基础路径生成板状方块的柱体、连接及端面模型。 */
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

	/** 按四向连接条件组合板状方块的柱体、连接和未连接变体。 */
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

	/** 分别用门的上半和下半纹理生成不同铰链侧与开合状态的模型。 */
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

	/** 以 [name] 为模型基础路径生成门的上下半、铰链侧及开合变体。 */
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

	/** 按门的上下半、铰链侧和开合状态在八种模型间选择，再按方块的朝向旋转各变体。 */
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

	/** 使用同一纹理生成活板门的上半、下半及打开模型。[orientable] 决定是否使用保留方向的模板。 */
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

	/** 以 [name] 为模型基础路径生成活板门的上半、下半及打开变体。 */
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

	/** 按上下半、开启状态和朝向使用已有的活板门模型。[orientable] 控制朝向变换。 */
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

		/** 为水平朝向属性创建绕纵轴的旋转规则，[angleOffset] 用于调整模型的基准朝向。 */
		fun rotationHorizontalFacing(angleOffset: Int): PropertyDispatch<VariantMutator> = PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
			.generate { direction -> yRot(direction, angleOffset) }

		/** 为六向朝向属性创建旋转规则。垂直方向使用固定旋转，水平方向应用角度偏移。 */
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

		/** 先按安装面倾转模型，再按水平方向旋转。安装到天花板时额外调整半圈。 */
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
