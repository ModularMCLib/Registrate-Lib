package com.modularmc.registrate.localization;

import com.modularmc.registrate.localization.terminology.Domain;
import com.modularmc.registrate.localization.terminology.LocalizationCatalog.GlossaryTerm;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** An immutable source declaration and its explicit translations, resolved against the owning mod's source locale. */
public record LocalizationDeclaration(String source, Map<MinecraftLocale, String> translations, List<Domain> domains, @Nullable TextType type, List<GlossaryTerm> glossary) {

    public LocalizationDeclaration {
        translations = Map.copyOf(translations);
    }

    public static LocalizationDeclaration sourceText(String source) {
        return new LocalizationDeclaration(source, Map.of(), List.of(), null, List.of());
    }

    @Accessors(fluent = true, chain = true)
    public static final class Builder {

        @Setter
        private @Nullable String source;
        private final Map<MinecraftLocale, String> translations = new Object2ObjectLinkedOpenHashMap<>();
        private @Nullable List<Domain> domains;
        @Setter
        private @Nullable TextType type;
        private @Nullable List<GlossaryTerm> glossary;
        @Setter
        private LocalizationProfile profile = LocalizationProfile.EMPTY;

        public Builder translation(MinecraftLocale locale, String text) {
            translations.put(locale, text);
            return this;
        }

        public Builder domain(Domain... domains) {
            this.domains = List.of(domains);
            return this;
        }

        public Builder glossary(GlossaryTerm... terms) {
            glossary = List.of(terms);
            return this;
        }

        public LocalizationDeclaration build() {
            return new LocalizationDeclaration(
                    Objects.requireNonNull(source, "Supply a localization source with source(String)"),
                    translations,
                    domains == null ? profile.domains() : domains,
                    type == null ? profile.type() : type,
                    glossary == null ? profile.glossary() : glossary);
        }
    }
}
