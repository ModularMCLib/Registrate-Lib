package com.modularmc.registrate.difficulty;

import net.minecraft.util.StringRepresentable;

import org.jspecify.annotations.NullMarked;

/**
 * Gives a mod's difficulty enum stable IDs for use in configuration.
 * IDs stay fixed after enum initialization and must be unique within the enum.
 */
@NullMarked
public interface DifficultyLevel extends StringRepresentable {

    /** Returns the non-blank ID used when reading a difficulty from configuration. */
    @Override
    String getSerializedName();
}
