package com.modularmc.registrate.providers.core;

import com.modularmc.registrate.localization.resolution.TranslationRegistry;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Groups Registrate providers by the output used by NeoForge. All GatherData callbacks have completed when
 * NeoForge starts running the first provider, including those attached to generators merged by --flat.
 */
final class DataGenBatch implements DataProvider {

    private static final Map<Path, DataGenBatch> PENDING = new Object2ObjectOpenHashMap<>();

    private final PackOutput output;
    private final Path root;
    private final List<RegistrateDataProvider> participants = new ArrayList<>();

    private DataGenBatch(PackOutput output, Path root) {
        this.output = output;
        this.root = root;
    }

    static synchronized void attach(GatherDataEvent event, RegistrateDataProvider provider) {
        var output = event.getGenerator().getPackOutput();
        var root = output.getOutputFolder().toAbsolutePath().normalize();
        var batch = PENDING.computeIfAbsent(root, path -> event.addProvider(new DataGenBatch(output, path)));
        batch.participants.add(provider);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        synchronized (DataGenBatch.class) {
            PENDING.remove(root);
        }
        participants.sort(Comparator.comparing(RegistrateDataProvider::getName));
        return run(cache, output, participants);
    }

    static CompletableFuture<?> run(CachedOutput cache, PackOutput output, List<RegistrateDataProvider> participants) {
        var work = participants.stream().map(provider -> provider.runResources(cache)).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(work).thenCompose(ignored -> {
            var translations = new TranslationRegistry();
            participants.forEach(provider -> provider.collectTranslations(translations));
            return LanguageOutput.write(cache, output, translations);
        });
    }

    @Override
    public String getName() {
        return "Registrate data and languages";
    }
}
