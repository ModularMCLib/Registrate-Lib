package com.modularmc.registrate.localization

import com.modularmc.registrate.localization.TranslationSource.Kind
import com.modularmc.registrate.util.nullness.NonNullSupplier

/** 构建器保存的本地化声明。数据生成时从已绑定的游戏对象读取翻译键。 */
@JvmRecord
data class EntryLocalization(val key: NonNullSupplier<String>, val declaration: NonNullSupplier<LocalizationDeclaration>, val kind: Kind)
