package com.modularmc.registrate.localization

import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.util.nullness.NonNullSupplier

/** A builder declaration whose key is obtained from the bound Minecraft object during DataGen. */
@JvmRecord
data class EntryLocalization(val key: NonNullSupplier<String>, val declaration: NonNullSupplier<LocalizationDeclaration>, val kind: Kind)
