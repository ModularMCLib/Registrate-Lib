package com.modularmc.registrate.providers.core;

import com.modularmc.registrate.localization.resolution.TranslationRegistry;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public final class LanguageOutput {

    private LanguageOutput() {}

    public static CompletableFuture<?> write(CachedOutput cache, PackOutput output, TranslationRegistry registry) {
        var paths = output.createPathProvider(Target.RESOURCE_PACK, "lang");
        var files = registry.resolve().entrySet().stream().map(entry -> {
            var file = entry.getKey();
            var path = paths.json(ResourceLocation.fromNamespaceAndPath(file.namespace(), file.locale().code()));
            var language = new JsonObject();
            entry.getValue().forEach(language::addProperty);
            return DataProvider.saveStable(cache, language, path);
        }).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(files);
    }
}
