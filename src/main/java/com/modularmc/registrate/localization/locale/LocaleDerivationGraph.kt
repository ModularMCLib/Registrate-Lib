package com.modularmc.registrate.localization.locale

import com.modularmc.registrate.localization.MinecraftLocale
import com.modularmc.registrate.localization.MinecraftLocale.EN_GB
import com.modularmc.registrate.localization.MinecraftLocale.EN_UD
import com.modularmc.registrate.localization.MinecraftLocale.ZH_CN
import com.modularmc.registrate.localization.MinecraftLocale.ZH_HK
import com.modularmc.registrate.localization.MinecraftLocale.ZH_TW

import it.unimi.dsi.fastutil.objects.ObjectArrayList

import java.util.EnumMap
import java.util.EnumSet

internal class LocaleDerivationGraph(modId: String, derivations: Collection<LocaleDerivation>) {
	private val outgoing = EnumMap<MinecraftLocale, ObjectArrayList<LocaleDerivation>>(MinecraftLocale::class.java)

	init {
		val byTarget = EnumMap<MinecraftLocale, LocaleDerivation>(MinecraftLocale::class.java)
		byTarget[EN_UD] = LocaleDerivation.UPSIDE_DOWN
		byTarget[EN_GB] = LocaleDerivation.BRITISH_ENGLISH
		byTarget[ZH_TW] = LocaleDerivation.TAIWAN_CHINESE
		for (derivation in derivations) byTarget[derivation.output] = derivation

		val done = EnumSet.noneOf(MinecraftLocale::class.java)
		val path = ObjectArrayList<MinecraftLocale>()
		val visiting = EnumSet.noneOf(MinecraftLocale::class.java)
		for (target in byTarget.keys) {
			var locale = target
			while (locale !in done) {
				val rule = byTarget[locale] ?: break
				require(visiting.add(locale)) {
					val cycleStart = path.indexOf(locale)
					val cycle = path.subList(cycleStart, path.size).joinToString(" -> ", transform = MinecraftLocale::code)
					"[$modId] Locale derivation cycle: $cycle -> ${locale.code()}"
				}
				path.add(locale)
				locale = rule.input
			}
			done.addAll(visiting)
			visiting.clear()
			path.clear()
		}
		// Keep custom paths that already derive Simplified Chinese from Hong Kong Chinese.
		if (ZH_HK !in byTarget && generateSequence(ZH_CN) { byTarget[it]?.input }.none { it == ZH_HK }) {
			byTarget[ZH_HK] = LocaleDerivation.HONG_KONG_CHINESE
		}
		for (derivation in byTarget.values) outgoing.computeIfAbsent(derivation.input) { ObjectArrayList(1) }.add(derivation)
	}

	fun from(locale: MinecraftLocale): List<LocaleDerivation> = outgoing[locale].orEmpty()

	companion object {
		val DEFAULT = LocaleDerivationGraph("registratelib", emptyList())
	}
}
