package com.modularmc.registrate.internal.util

import com.modularmc.registrate.RegistrateLib

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object RegistrateLogger {
	private val ROOT = RegistrateLib.LOGGER
	private val CORE = child("Core")
	private val DATAGEN = child("Datagen")
	private val REGISTRATION = child("Registration")

	@JvmStatic
	fun root(): Logger = ROOT

	@JvmStatic
	fun core(): Logger = CORE

	@JvmStatic
	fun datagen(): Logger = DATAGEN

	@JvmStatic
	fun registration(): Logger = REGISTRATION

	@JvmStatic
	fun modMessage(message: String): String = "[{}] $message"

	private fun child(name: String): Logger = LogManager.getLogger(ROOT.name + "." + name)
}
