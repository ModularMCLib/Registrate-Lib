package com.modularmc.registrate.providers.generators;

import com.modularmc.registrate.AbstractRegistrate;
import com.modularmc.registrate.providers.core.ProviderType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;
import net.neoforged.neoforge.client.model.generators.VariantBlockStateBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import com.google.gson.JsonElement;

import java.util.Optional;

public class RegistrateBlockModelGenerator extends BlockStateProvider {

    private final AbstractRegistrate<?> parent;
    private final RegistrateItemModelGenerator itemModels;

    public RegistrateBlockModelGenerator(AbstractRegistrate<?> parent, PackOutput output, ExistingFileHelper fileHelper) {
        super(output, parent.getModid(), fileHelper);
        this.parent = parent;
        this.itemModels = new RegistrateItemModelGenerator(parent, output, fileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        parent.genData(ProviderType.BLOCKSTATE, this);
        itemModels.registerModels();
    }

    @Override
    public RegistrateItemModelGenerator itemModels() {
        return itemModels;
    }

    public void create(Block block, ResourceLocation model) {
        simpleBlock(block, models().getExistingFile(model));
    }

    public void createTrivialCube(Block block) {
        simpleBlock(block, models().cubeAll(modelLocation(block).getPath(), blockTexture(block)));
    }

    public ResourceLocation mcBlockTexture(String path) {
        return mcLoc("block/" + path);
    }

    public ResourceLocation modBlockTexture(String path) {
        return modLoc("block/" + path);
    }

    public ResourceLocation blockTexture(Block block, String suffix) {
        return blockTexture(block).withSuffix(suffix);
    }

    public static ResourceLocation modelLocation(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).withPrefix("block/");
    }

    public Optional<VariantBlockStateBuilder> getExistingVariantBuilder(Block block) {
        return Optional.ofNullable(registeredBlocks.get(block))
                .filter(VariantBlockStateBuilder.class::isInstance)
                .map(VariantBlockStateBuilder.class::cast);
    }

    public Optional<MultiPartBlockStateBuilder> getExistingMultipartBuilder(Block block) {
        return Optional.ofNullable(registeredBlocks.get(block))
                .filter(MultiPartBlockStateBuilder.class::isInstance)
                .map(MultiPartBlockStateBuilder.class::cast);
    }

    public Optional<ResourceLocation> getSingleVariantModel(Block block) {
        return getExistingVariantBuilder(block)
                .map(builder -> builder.getModels().get(builder.partialState()))
                .map(ConfiguredModelList::toJSON)
                .filter(JsonElement::isJsonObject)
                .map(json -> json.getAsJsonObject().get("model"))
                .map(JsonElement::getAsString)
                .map(ResourceLocation::parse);
    }
}
