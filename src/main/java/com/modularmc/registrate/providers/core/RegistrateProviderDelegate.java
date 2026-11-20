package com.modularmc.registrate.providers.core;

import net.minecraft.data.DataProvider;
import net.minecraft.resources.ResourceLocation;

public interface RegistrateProviderDelegate<R, T extends R> extends DataProvider {

    String getName();

    ResourceLocation getId();

    T getEntry();
}
