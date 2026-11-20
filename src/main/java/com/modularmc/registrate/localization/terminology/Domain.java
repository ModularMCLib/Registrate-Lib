package com.modularmc.registrate.localization.terminology;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Built-in fields used to associate terms and localization declarations. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum Domain {

    CHEMISTRY("chemistry"),
    PHYSICS("physics"),
    ENERGY("energy"),
    ASTRONOMY("astronomy"),
    GEOGRAPHY("geography"),
    GEOLOGY("geology"),
    ENGINEERING("engineering"),
    MATERIALS("materials"),
    COMPUTING("computing"),
    MAGIC("magic"),
    ADVENTURE("adventure");

    private final String id;
}
