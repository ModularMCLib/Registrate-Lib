package com.modularmc.registrate.localization;

import com.modularmc.registrate.localization.TranslationSource.Kind;
import com.modularmc.registrate.util.nullness.NonNullSupplier;

/**
 * A builder declaration saved at submission. Its key comes from the bound Minecraft object during data generation.
 */
public record EntryLocalization(NonNullSupplier<String> key, NonNullSupplier<LocalizationDeclaration> declaration, Kind kind) {}
