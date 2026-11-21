package com.modularmc.registrate.providers.core

import net.minecraft.data.DataProvider
import net.minecraft.resources.Identifier

/** A provider delegate associated with a particular registered entry. */
interface RegistrateProviderDelegate<R : Any, T : R> : DataProvider {
	override fun getName(): String

	fun getId(): Identifier

	fun getEntry(): T
}
