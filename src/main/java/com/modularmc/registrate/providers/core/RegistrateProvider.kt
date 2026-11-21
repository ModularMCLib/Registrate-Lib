package com.modularmc.registrate.providers.core

import net.minecraft.data.DataProvider
import net.neoforged.fml.LogicalSide

/** A DataGen provider selected by the logical side requested by NeoForge. */
interface RegistrateProvider : DataProvider {
	fun getSide(): LogicalSide
}
