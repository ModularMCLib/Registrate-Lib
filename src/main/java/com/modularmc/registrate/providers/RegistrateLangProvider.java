package com.modularmc.registrate.providers;

import com.modularmc.registrate.AbstractRegistrate;
import com.modularmc.registrate.localization.LocalizationDeclaration;
import com.modularmc.registrate.localization.TranslationKey;
import com.modularmc.registrate.localization.TranslationSource;
import com.modularmc.registrate.localization.TranslationSource.Kind;
import com.modularmc.registrate.localization.index.IndexedTranslations;
import com.modularmc.registrate.localization.resolution.TranslationRegistry;
import com.modularmc.registrate.providers.core.LanguageOutput;
import com.modularmc.registrate.providers.core.ProviderType;
import com.modularmc.registrate.providers.core.RegistrateProvider;
import com.modularmc.registrate.util.nullness.NonNullSupplier;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.data.LanguageProvider;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RegistrateLangProvider extends LanguageProvider implements RegistrateProvider {

    private final AbstractRegistrate<?> owner;
    private final PackOutput packOutput;
    private final List<Operation> operations = new ArrayList<>();

    private record Operation(String key, String value, TranslationSource source) {}

    public RegistrateLangProvider(AbstractRegistrate<?> owner, PackOutput packOutput) {
        super(packOutput, owner.getModid(), "en_us");
        this.owner = owner;
        this.packOutput = packOutput;
    }

    @Override
    public LogicalSide getSide() {
        return LogicalSide.CLIENT;
    }

    @Override
    public String getName() {
        return "Registrate language declarations";
    }

    @Override
    protected void addTranslations() {
        owner.genData(ProviderType.LANG, this);
    }

    public static final String toEnglishName(String internalName) {
        return Arrays.stream(internalName.toLowerCase(Locale.ROOT).split("_"))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining(" "));
    }

    @SuppressWarnings({ "unchecked", "ConstantConditions" })
    public <T> String getAutomaticName(NonNullSupplier<? extends T> sup, ResourceKey<? extends Registry<T>> registry) {
        return toEnglishName(((Registry<Registry<T>>) BuiltInRegistries.REGISTRY).get(registry.location()).getKey(sup.get()).getPath());
    }

    public void addBlock(NonNullSupplier<? extends Block> block) {
        addBlock(block, getAutomaticName(block, Registries.BLOCK));
    }

    public void addBlockWithTooltip(NonNullSupplier<? extends Block> block, String tooltip) {
        addBlock(block);
        addTooltip(block, tooltip);
    }

    public void addBlockWithTooltip(NonNullSupplier<? extends Block> block, String name, String tooltip) {
        addBlock(block, name);
        addTooltip(block, tooltip);
    }

    public void addItem(NonNullSupplier<? extends Item> item) {
        addItem(item, getAutomaticName(item, Registries.ITEM));
    }

    public void addItemWithTooltip(NonNullSupplier<? extends Item> block, String name, List<String> tooltip) {
        addItem(block, name);
        addTooltip(block, tooltip);
    }

    public void addTooltip(NonNullSupplier<? extends ItemLike> item, String tooltip) {
        add(item.get().asItem().getDescriptionId() + ".desc", tooltip);
    }

    public void addTooltip(NonNullSupplier<? extends ItemLike> item, List<String> tooltip) {
        for (int i = 0; i < tooltip.size(); i++) {
            add(item.get().asItem().getDescriptionId() + ".desc." + i, tooltip.get(i));
        }
    }

    public void add(CreativeModeTab tab, String name) {
        var contents = tab.getDisplayName().getContents();
        if (contents instanceof TranslatableContents lang) {
            add(lang.getKey(), name);
        } else {
            throw new IllegalArgumentException("Creative tab does not have a translatable name: " + tab.getDisplayName());
        }
    }

    public void addEntityType(NonNullSupplier<? extends EntityType<?>> entity) {
        addEntityType(entity, getAutomaticName(entity, Registries.ENTITY_TYPE));
    }

    @Override
    public void add(String key, String value) {
        operations.add(new Operation(key, value, new TranslationSource(Kind.DECLARED, "[" + owner.getModid() + "] ProviderType.LANG (" + key + ")")));
    }

    public void collect(TranslationRegistry registry) {
        var locale = owner.requireSourceLocale();
        owner.collectEntryTranslations(registry, locale);
        addTranslations();
        for (var operation : operations) {
            registry.add(new TranslationKey(owner.getModid(), operation.key()), locale, LocalizationDeclaration.sourceText(operation.value()), operation.source());
        }
        operations.clear();
        owner.collectRawTranslations(registry, locale);
        IndexedTranslations.collect(owner.getModid(), locale, registry);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        var registry = new TranslationRegistry();
        collect(registry);
        return LanguageOutput.write(cache, packOutput, registry);
    }
}
