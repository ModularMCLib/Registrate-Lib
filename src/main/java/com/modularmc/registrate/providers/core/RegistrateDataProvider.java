package com.modularmc.registrate.providers.core;

import com.modularmc.registrate.AbstractRegistrate;
import com.modularmc.registrate.internal.util.DebugMarkers;
import com.modularmc.registrate.internal.util.RegistrateLogger;
import com.modularmc.registrate.localization.resolution.TranslationRegistry;
import com.modularmc.registrate.providers.RegistrateLangProvider;
import com.modularmc.registrate.providers.RegistrateTagsProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RegistrateDataProvider implements DataProvider {

    private static final Logger LOGGER = RegistrateLogger.datagen();

    @SuppressWarnings("null")
    static final BiMap<String, ProviderType<?>> TYPES = HashBiMap.create();

    static final Map<ResourceKey<? extends Registry<?>>, ProviderType<?>> TAG_TYPES = new ConcurrentHashMap<>();
    static final Set<ProviderType<?>> CLIENT_TYPES = new ObjectOpenHashSet<>();

    public static @Nullable String getTypeName(GeneratorType<?> type) {
        if (type instanceof ProviderType<?> prov)
            return TYPES.inverse().get(prov);
        return type.toString();
    }

    private final String mod;
    private final PackOutput output;
    private final Map<ProviderType<?>, RegistrateProvider> subProviders = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<ProviderType<?>, ProviderType<?>> prerequisites = new Object2ObjectOpenHashMap<>();
    private final Map<GeneratorType<?>, Object> subGenerators = new ConcurrentHashMap<>();
    private final CompletableFuture<HolderLookup.Provider> registriesLookup;

    public RegistrateDataProvider(AbstractRegistrate<?> parent, String modid, GatherDataEvent event) {
        this.mod = modid;
        this.output = event.getGenerator().getPackOutput();
        this.registriesLookup = event.getLookupProvider();

        LOGGER.debug(DebugMarkers.DATA, RegistrateLogger.modMessage("Collecting datagen providers"), mod);
        Map<ProviderType<?>, RegistrateProvider> known = new Object2ObjectOpenHashMap<>();
        for (DataProviderInitializer.Sorted sorted : parent.getDataGenInitializer().getSortedProviders()) {
            ProviderType<?> type = sorted.type();
            if (CLIENT_TYPES.contains(type) && !event.includeClient()) {
                continue;
            }
            var lookup = registriesLookup;
            if (known.get(sorted.parent()) instanceof RegistrateLookupFillerProvider dependency) {
                lookup = dependency.getFilledProvider();
            }
            RegistrateProvider prov = ProviderType.create(type, parent, known, output, lookup, event.getExistingFileHelper());
            if (prov instanceof RegistrateTagsProvider<?> tagsProvider && TAG_TYPES.get(tagsProvider.registry()) != type) {
                throw new IllegalStateException("Tag providers must be registered through ProviderType::registerTag");
            }
            known.put(type, prov);
            if (prov.getSide() == LogicalSide.CLIENT ? event.includeClient() : event.includeServer()) {
                if (sorted.parent() != null && !subProviders.containsKey(sorted.parent())) {
                    throw new IllegalStateException("[%s] Provider %s requires disabled provider %s"
                            .formatted(mod, sorted.id(), getTypeName(sorted.parent())));
                }
                prerequisites.put(type, sorted.parent());
                LOGGER.debug(
                        DebugMarkers.DATA,
                        RegistrateLogger.modMessage("Added datagen provider {}"),
                        mod,
                        sorted.id());
                subProviders.put(type, prov);
            }
        }
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return DataGenBatch.run(cache, output, List.of(this));
    }

    public void attach(GatherDataEvent event) {
        DataGenBatch.attach(event, this);
    }

    CompletableFuture<?> runResources(CachedOutput cache) {
        return registriesLookup.thenCompose(provider -> {
            var runs = new Object2ObjectOpenHashMap<ProviderType<?>, CompletableFuture<?>>(subProviders.size());
            for (var entry : subProviders.entrySet()) {
                if (entry.getKey() == ProviderType.LANG) {
                    continue;
                }
                var dependency = prerequisites.get(entry.getKey());
                var future = dependency == null ? entry.getValue().run(cache) : runs.get(dependency).thenCompose(ignored -> entry.getValue().run(cache));
                runs.put(entry.getKey(), future);
            }
            return CompletableFuture.allOf(runs.values().toArray(CompletableFuture[]::new));
        });
    }

    void collectTranslations(TranslationRegistry registry) {
        if (subProviders.get(ProviderType.LANG) instanceof RegistrateLangProvider language) {
            language.collect(registry);
        }
    }

    @Override
    public String getName() {
        return "Registrate Provider for " + mod;
    }

    @SuppressWarnings("unchecked")
    public <P> Optional<P> getSubProvider(GeneratorType<P> type) {
        if (type instanceof ProviderType<?> prov)
            return Optional.ofNullable((P) subProviders.get(prov));
        return Optional.ofNullable((P) subGenerators.get(type));
    }

    public <T> void putSubProvider(GeneratorType<? extends T> type, T gen) {
        if (!(type instanceof ProviderType<?>)) {
            subGenerators.put(type, gen);
        }
    }
}
