package com.modularmc.registrate.providers.generators;

import com.modularmc.registrate.AbstractRegistrate;
import com.modularmc.registrate.providers.core.ProviderType;
import com.modularmc.registrate.util.nullness.NonNullSupplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.loaders.DynamicFluidContainerModelBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.function.UnaryOperator;

public class RegistrateItemModelGenerator extends ItemModelProvider {

    private final AbstractRegistrate<?> parent;

    public RegistrateItemModelGenerator(AbstractRegistrate<?> parent, PackOutput output, ExistingFileHelper fileHelper) {
        super(output, parent.getModid(), fileHelper);
        this.parent = parent;
    }

    @Override
    protected void registerModels() {
        parent.genData(ProviderType.ITEM_MODEL, this);
    }

    public boolean hasModel(Item item) {
        return generatedModels.containsKey(itemLocation(item));
    }

    public ItemModelBuilder createWithExistingModel(Item item, ResourceLocation model) {
        return withExistingParent(itemLocation(item).toString(), model);
    }

    public ItemModelBuilder generateFlatItem(Item item, ResourceLocation texture) {
        return generateFlatItem(item, mcLoc("item/generated"), texture);
    }

    public ItemModelBuilder generateFlatItem(Item item, ResourceLocation parentModel, ResourceLocation texture) {
        return createWithExistingModel(item, parentModel).texture("layer0", texture);
    }

    public ItemModelBuilder generateFlatBlockItem(BlockItem item) {
        return generateFlatBlockItem(item, "");
    }

    public ItemModelBuilder generateFlatBlockItem(BlockItem item, String suffix) {
        return generateFlatItem(item, RegistrateBlockModelGenerator.modelLocation(item.getBlock()).withSuffix(suffix));
    }

    public ItemModelBuilder generateBlockItem(BlockItem item, UnaryOperator<ResourceLocation> modelMapper) {
        return createWithExistingModel(item, modelMapper.apply(RegistrateBlockModelGenerator.modelLocation(item.getBlock())));
    }

    public ItemModelBuilder generateBlockItem(BlockItem item, String suffix) {
        return generateBlockItem(item, model -> model.withSuffix(suffix));
    }

    public ResourceLocation mcBlockTexture(String path) {
        return mcLoc("block/" + path);
    }

    public ResourceLocation modBlockTexture(String path) {
        return modLoc("block/" + path);
    }

    public ResourceLocation mcItemTexture(String path) {
        return mcLoc("item/" + path);
    }

    public ResourceLocation modItemTexture(String path) {
        return modLoc("item/" + path);
    }

    public String modid(NonNullSupplier<? extends ItemLike> item) {
        return BuiltInRegistries.ITEM.getKey(item.get().asItem()).getNamespace();
    }

    public String name(NonNullSupplier<? extends ItemLike> item) {
        return BuiltInRegistries.ITEM.getKey(item.get().asItem()).getPath();
    }

    public void generateFluidBucket(Item item, Fluid fluid) {
        getBuilder(itemLocation(item).toString())
                .texture("particle", mcItemTexture("bucket"))
                .texture("base", mcItemTexture("bucket"))
                .texture("fluid", ResourceLocation.fromNamespaceAndPath("neoforge", "item/mask/bucket_fluid"))
                .customLoader(DynamicFluidContainerModelBuilder::begin)
                .fluid(fluid)
                .flipGas(true)
                .applyFluidLuminosity(true)
                .end();
    }

    private static ResourceLocation itemLocation(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).withPrefix("item/");
    }
}
