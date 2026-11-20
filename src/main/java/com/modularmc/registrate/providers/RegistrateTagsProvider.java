package com.modularmc.registrate.providers;

import com.modularmc.registrate.AbstractRegistrate;
import com.modularmc.registrate.providers.core.ProviderType;
import com.modularmc.registrate.providers.core.RegistrateLookupFillerProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider.IntrinsicTagAppender;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.data.tags.TagsProvider.TagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface RegistrateTagsProvider<T> extends RegistrateLookupFillerProvider {

    CompletableFuture<TagsProvider.TagLookup<T>> contentsGetter();

    ResourceKey<? extends Registry<T>> registry();

    TagBuilder rawBuilder(TagKey<T> key);

    interface Key<T> extends RegistrateTagsProvider<T> {

        TagAppender<T> tag(TagKey<T> key);
    }

    interface Intrinsic<T> extends RegistrateTagsProvider<T> {

        IntrinsicTagAppender<T> tag(TagKey<T> key);
    }

    class KeyTags<T> extends TagsProvider<T> implements RegistrateTagsProvider.Key<T> {

        private final AbstractRegistrate<?> owner;
        private final ProviderType<? extends KeyTags<T>> type;
        private final String name;

        public KeyTags(AbstractRegistrate<?> owner, ProviderType<? extends KeyTags<T>> type, String name, PackOutput packOutput, ResourceKey<? extends Registry<T>> registryIn, CompletableFuture<HolderLookup.Provider> registriesLookup, ExistingFileHelper fileHelper) {
            super(packOutput, registryIn, registriesLookup, owner.getModid(), fileHelper);

            this.owner = owner;
            this.type = type;
            this.name = name;
        }

        @Override
        public String getName() {
            return "Tags (%s)".formatted(name);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            owner.genData(type, this);
        }

        @Override
        public LogicalSide getSide() {
            return LogicalSide.SERVER;
        }

        @Override
        public TagBuilder rawBuilder(final TagKey<T> key) {
            return super.getOrCreateRawBuilder(key);
        }

        @Override
        public TagAppender<T> tag(TagKey<T> key) {
            return super.tag(key);
        }

        @Override
        public CompletableFuture<HolderLookup.Provider> getFilledProvider() {
            return createContentsProvider();
        }

        @Override
        public ResourceKey<? extends Registry<T>> registry() {
            return registryKey;
        }
    }

    class IntrinsicTags<T> extends IntrinsicHolderTagsProvider<T> implements RegistrateTagsProvider.Intrinsic<T> {

        private final AbstractRegistrate<?> owner;
        private final ProviderType<? extends IntrinsicTags<T>> type;
        private final String name;

        public IntrinsicTags(AbstractRegistrate<?> owner, ProviderType<? extends IntrinsicTags<T>> type, String name, PackOutput packOutput, ResourceKey<? extends Registry<T>> registryIn, CompletableFuture<HolderLookup.Provider> registriesLookup, Function<T, ResourceKey<T>> keyExtractor, ExistingFileHelper fileHelper) {
            super(packOutput, registryIn, registriesLookup, keyExtractor, owner.getModid(), fileHelper);

            this.owner = owner;
            this.type = type;
            this.name = name;
        }

        @Override
        public String getName() {
            return "Tags (%s)".formatted(name);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            owner.genData(type, this);
        }

        @Override
        public LogicalSide getSide() {
            return LogicalSide.SERVER;
        }

        @Override
        public TagBuilder rawBuilder(TagKey<T> key) {
            return super.getOrCreateRawBuilder(key);
        }

        @Override
        public IntrinsicTagAppender<T> tag(final TagKey<T> key) {
            return super.tag(key);
        }

        @Override
        public CompletableFuture<HolderLookup.Provider> getFilledProvider() {
            return createContentsProvider();
        }

        @Override
        public ResourceKey<? extends Registry<T>> registry() {
            return registryKey;
        }
    }
}
