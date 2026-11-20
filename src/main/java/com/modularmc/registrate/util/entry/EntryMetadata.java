package com.modularmc.registrate.util.entry;

import com.modularmc.registrate.providers.core.GeneratorType;
import com.modularmc.registrate.util.nullness.NonNullConsumer;

import java.util.List;

/**
 * Declarations captured when an entry is submitted. Callbacks can refer to registry objects that only become
 * available during data generation.
 */
public record EntryMetadata(List<DataGenerator<?>> dataGenerators) {

    public EntryMetadata {
        dataGenerators = List.copyOf(dataGenerators);
    }

    public record DataGenerator<P>(GeneratorType<? extends P> type, NonNullConsumer<? extends P> callback) {}
}
