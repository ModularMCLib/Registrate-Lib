package com.modularmc.registrate.localization.resolution;

import com.modularmc.registrate.localization.TranslationKey;
import com.modularmc.registrate.localization.TranslationSource;
import com.modularmc.registrate.localization.TranslationSource.Kind;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Collects the language declarations for one generation output and resolves them before any language file is written.
 */
public final class TranslationRegistry {

    private static final Pattern LOCALE = Pattern.compile("[a-z]{2,3}(?:_[a-z0-9]{2,8})*");

    private final Map<EntryKey, Selection> selections = new Object2ObjectLinkedOpenHashMap<>();

    public record LanguageFile(String namespace, String locale) {}

    private record EntryKey(TranslationKey key, String locale) {

        private String describe() {
            return "%s/%s (%s)".formatted(key.namespace(), key.value(), locale);
        }
    }

    private record Candidate(String text, @Nullable String sourceLocale, TranslationSource source) {}

    private static final class Selection {

        private Kind priority;
        private final List<Candidate> candidates = new ArrayList<>(1);

        private Selection(Candidate candidate) {
            priority = candidate.source().kind();
            candidates.add(candidate);
        }

        private void add(Candidate candidate) {
            int precedence = candidate.source().kind().compareTo(priority);
            if (precedence > 0) {
                candidates.clear();
                priority = candidate.source().kind();
            }
            if (precedence >= 0) {
                candidates.add(candidate);
            }
        }
    }

    public static String requireLocale(@Nullable String locale, String location) {
        if (locale == null || !LOCALE.matcher(locale).matches()) {
            throw new IllegalArgumentException("Missing or invalid Minecraft locale '%s' for %s".formatted(locale, location));
        }
        return locale;
    }

    /**
     * Adds a candidate. An explicit translation identifies the source locale of its declaration so the final
     * translation can be checked against the selected source text for the same key.
     */
    public void add(TranslationKey key, String locale, String text, @Nullable String sourceLocale, TranslationSource source) {
        requireLocale(locale, source.location());
        var entry = new EntryKey(key, locale);
        var candidate = new Candidate(text, sourceLocale, source);
        var selection = selections.get(entry);
        if (selection == null) {
            selections.put(entry, new Selection(candidate));
        } else {
            selection.add(candidate);
        }
    }

    public Map<LanguageFile, Map<String, String>> resolve() {
        var formats = new Object2ObjectLinkedOpenHashMap<EntryKey, MinecraftTextFormat.Parsed>(selections.size());
        var files = new Object2ObjectLinkedOpenHashMap<LanguageFile, Map<String, String>>();
        selections.forEach((key, selection) -> {
            var first = selection.candidates.getFirst();
            for (var candidate : selection.candidates) {
                if (!first.text().equals(candidate.text())) {
                    throw new IllegalArgumentException("Conflicting translations for %s from %s and %s"
                            .formatted(key.describe(), first.source().location(), candidate.source().location()));
                }
            }
            formats.put(key, MinecraftTextFormat.parse(first.text(), key.describe() + " at " + first.source().location()));
            put(files, key, first.text());
        });
        selections.forEach((key, selection) -> {
            for (var candidate : selection.candidates) {
                if (candidate.sourceLocale() != null) {
                    var source = formats.get(new EntryKey(key.key(), candidate.sourceLocale()));
                    if (!source.signature().equals(formats.get(key).signature())) {
                        throw new IllegalArgumentException("Minecraft placeholder mismatch for %s at %s"
                                .formatted(key.describe(), candidate.source().location()));
                    }
                }
            }
            if ("en_us".equals(key.locale())) {
                var upsideDown = new EntryKey(key.key(), "en_ud");
                if (!selections.containsKey(upsideDown)) {
                    put(files, upsideDown, MinecraftTextFormat.upsideDown(selection.candidates.getFirst().text(), formats.get(key)));
                }
            }
        });
        return files;
    }

    private static void put(Map<LanguageFile, Map<String, String>> files, EntryKey key, String text) {
        files.computeIfAbsent(new LanguageFile(key.key().namespace(), key.locale()), ignored -> new TreeMap<>()).put(key.key().value(), text);
    }
}
