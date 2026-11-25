package com.modularmc.registrate.internal.util

import com.modularmc.registrate.RegistrateLib

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/** 集中提供 Registrate 各阶段的日志器和带模组标识的消息格式。 */
object RegistrateLogger {
	private val ROOT = RegistrateLib.LOGGER
	private val CORE = child("Core")
	private val DATAGEN = child("Datagen")
	private val REGISTRATION = child("Registration")

	/** 返回本库使用的根日志器。 */
	@JvmStatic
	fun root(): Logger = ROOT

	/** 返回用于记录 Registrate 初始化和公共流程的日志器。 */
	@JvmStatic
	fun core(): Logger = CORE

	/** 返回用于记录数据生成及本地化解析的日志器。 */
	@JvmStatic
	fun datagen(): Logger = DATAGEN

	/** 返回用于记录注册提交、绑定及回调的日志器。 */
	@JvmStatic
	fun registration(): Logger = REGISTRATION

	/** 为日志消息加上模组标识占位符。调用记录方法时须将模组标识作为第一个格式化参数。 */
	@JvmStatic
	fun modMessage(message: String): String = "[{}] $message"

	private fun child(name: String): Logger = LogManager.getLogger(ROOT.name + "." + name)
}
