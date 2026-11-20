package com.modularmc.registrate.localization;

/** Identifies a translation's priority and the declaration that supplied it. */
public record TranslationSource(Kind kind, String location) {

    public enum Kind {
        VARIANT,
        AUTOMATIC,
        DECLARED
    }
}
