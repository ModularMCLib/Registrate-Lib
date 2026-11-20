package com.modularmc.registrate.util.entry;

import com.modularmc.registrate.localization.EntryLocalization;
import com.modularmc.registrate.providers.core.GeneratorType;
import com.modularmc.registrate.util.nullness.NonNullConsumer;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Declarations associated with an entry. The data generator collection is a read-only view of the registration.
 */
public record EntryMetadata(Collection<DataGenerator<?>> dataGenerators, @Nullable EntryLocalization localization) {

    public EntryMetadata {
        dataGenerators = Collections.unmodifiableCollection(dataGenerators);
    }

    public record DataGenerator<P>(GeneratorType<? extends P> type, NonNullConsumer<? extends P> callback) {}
}
