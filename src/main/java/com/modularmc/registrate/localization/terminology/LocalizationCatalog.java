package com.modularmc.registrate.localization.terminology;

import com.modularmc.registrate.localization.MinecraftLocale;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Terminology defined by one mod. Register it during mod setup and query it alongside declarations. */
public final class LocalizationCatalog {

    public record GlossaryTerm(String id, String source, List<Domain> domains, Map<MinecraftLocale, String> preferredTranslations) {}

    public record Unit(String id, String symbol, Map<MinecraftLocale, String> names) {}

    private final EnumSet<Domain> domains = EnumSet.noneOf(Domain.class);
    private final Map<String, GlossaryTerm> terms = new Object2ObjectOpenHashMap<>();
    private final Map<String, Unit> units = new Object2ObjectOpenHashMap<>();

    public void register(Domain domain) {
        domains.add(domain);
    }

    public void register(GlossaryTerm term) {
        terms.put(term.id(), term);
    }

    public void register(Unit unit) {
        units.put(unit.id(), unit);
    }

    public boolean hasDomain(Domain domain) {
        return domains.contains(domain);
    }

    public Optional<GlossaryTerm> glossary(String id) {
        return Optional.ofNullable(terms.get(id));
    }

    public Optional<Unit> unit(String id) {
        return Optional.ofNullable(units.get(id));
    }
}
