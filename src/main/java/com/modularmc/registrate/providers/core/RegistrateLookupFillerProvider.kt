package com.modularmc.registrate.providers.core

import net.minecraft.core.HolderLookup

import java.util.concurrent.CompletableFuture

/** Supplies the registry lookups to use by dependent providers during DataGen. */
interface RegistrateLookupFillerProvider : RegistrateProvider {
	fun getFilledProvider(): CompletableFuture<HolderLookup.Provider>
}
