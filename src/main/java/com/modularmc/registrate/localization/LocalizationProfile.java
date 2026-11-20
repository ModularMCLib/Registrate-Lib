package com.modularmc.registrate.localization;

import com.modularmc.registrate.localization.terminology.Domain;
import com.modularmc.registrate.localization.terminology.LocalizationCatalog.GlossaryTerm;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Reusable domain, text type and glossary defaults for a localization declaration. */
public record LocalizationProfile(List<Domain> domains, @Nullable TextType type, List<GlossaryTerm> glossary) {

    static final LocalizationProfile EMPTY = new LocalizationProfile(List.of(), null, List.of());
}
