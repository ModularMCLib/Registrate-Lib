package com.modularmc.registrate.providers.generators;

import com.modularmc.registrate.AbstractRegistrate;
import com.modularmc.registrate.providers.core.RegistrateProvider;

import net.minecraft.data.PackOutput;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class RegistrateModelProvider extends RegistrateBlockModelGenerator implements RegistrateProvider {

    public RegistrateModelProvider(AbstractRegistrate<?> parent, PackOutput output, ExistingFileHelper fileHelper) {
        super(parent, output, fileHelper);
    }

    @Override
    public String getName() {
        return "Blockstates and item models";
    }

    @Override
    public LogicalSide getSide() {
        return LogicalSide.CLIENT;
    }
}
