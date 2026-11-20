package com.modularmc.registrate.localization.index;

import com.modularmc.registrate.localization.LocalizationDeclaration;
import com.modularmc.registrate.localization.MinecraftLocale;
import com.modularmc.registrate.localization.TranslationKey;
import com.modularmc.registrate.localization.TranslationSource;
import com.modularmc.registrate.localization.TranslationSource.Kind;
import com.modularmc.registrate.localization.resolution.TranslationRegistry;

import net.neoforged.fml.ModList;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;

/** Loads the declarations compiled into a mod's own resources during DataGen. */
public final class IndexedTranslations {

    private IndexedTranslations() {}

    public static void collect(String modId, MinecraftLocale locale, TranslationRegistry registry) {
        var modFile = ModList.get().getModFileById(modId).getFile();
        var index = modFile.findResource(LocalizationIndex.path(modId));
        if (Files.notExists(index)) {
            return;
        }

        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(index)))) {
            LocalizationIndex.read(input, entry -> registry.add(
                    new TranslationKey(entry.namespace(), entry.key()),
                    locale,
                    new LocalizationDeclaration(entry.source(), entry.translations(), List.of(), null, List.of()),
                    new TranslationSource(Kind.DECLARED, entry.location())));
        } catch (IOException error) {
            throw new UncheckedIOException("[" + modId + "] Reading localization index " + index, error);
        }
    }
}
