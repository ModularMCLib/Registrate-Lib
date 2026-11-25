package com.modularmc.registrate.difficulty;

import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps the difficulty levels in a mod's configuration to typed gameplay values.
 * The table can be read by multiple threads after construction. Values are stored by reference.
 *
 * @param <D> the mod's difficulty enum
 * @param <V> the value type shared by the levels
 */
@NullMarked
public final class DifficultyValues<D extends Enum<D> & DifficultyLevel, V> {

    private final Map<String, D> levelsById;
    private final EnumMap<D, V> valuesByLevel;

    /**
     * Snapshots the supplied levels and values. Several levels may share the same value.
     *
     * @param levels declared levels, typically from the enum's {@code values()} method
     * @param values a value for every declared level
     * @throws IllegalArgumentException if fewer than two levels are supplied, an ID is blank or repeated, or a value is missing
     */
    public DifficultyValues(D[] levels, Map<D, ? extends V> values) {
        if (levels.length < 2) throw new IllegalArgumentException("At least two difficulty levels are required");

        levelsById = LinkedHashMap.newLinkedHashMap(levels.length);
        valuesByLevel = new EnumMap<>(levels[0].getDeclaringClass());
        for (D level : levels) {
            String id = level.getSerializedName();
            if (id.isBlank()) throw new IllegalArgumentException("A difficulty ID is required for " + level.name());
            if (levelsById.putIfAbsent(id, level) != null) throw new IllegalArgumentException("Duplicate difficulty ID '" + id + "'");

            V value = values.get(level);
            if (value == null) throw new IllegalArgumentException("Missing value for difficulty '" + id + "'");
            valuesByLevel.put(level, value);
        }
    }

    /**
     * Resolves a case-sensitive difficulty ID from configuration.
     *
     * @throws IllegalArgumentException if the ID is unknown, with the available IDs in the message
     */
    public D level(String id) {
        D level = levelsById.get(id);
        if (level == null) throw new IllegalArgumentException("Unknown difficulty '" + id + "'. Available IDs " + levelsById.keySet());
        return level;
    }

    /**
     * Returns the value for the selected difficulty.
     *
     * @throws IllegalArgumentException if the level was omitted when constructing this table
     */
    public V value(D level) {
        V value = valuesByLevel.get(level);
        if (value == null) throw new IllegalArgumentException("Undeclared difficulty '" + level.getSerializedName() + "'");
        return value;
    }
}
