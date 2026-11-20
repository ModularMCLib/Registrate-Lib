package com.modularmc.registrate.providers.generators

import com.modularmc.registrate.AbstractRegistrate
import com.modularmc.registrate.providers.core.RegistrateProvider

import net.minecraft.data.PackOutput
import net.neoforged.fml.LogicalSide
import net.neoforged.neoforge.common.data.ExistingFileHelper

open class RegistrateModelProvider(parent: AbstractRegistrate<*>, output: PackOutput, fileHelper: ExistingFileHelper) :
	RegistrateBlockModelGenerator(parent, output, fileHelper),
	RegistrateProvider {
	override fun getName(): String = "Blockstates and item models"

	override fun getSide(): LogicalSide = LogicalSide.CLIENT
}
