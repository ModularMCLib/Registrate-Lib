package com.modularmc.registrate.difficulty;

import net.minecraft.util.StringRepresentable;

import org.jspecify.annotations.NullMarked;

/**
 * 由模组的难度枚举实现，供配置按稳定标识选择档位。
 * 调用方保存 {@link #getSerializedName()} 返回的标识，枚举常量改名或调整顺序后仍可沿用已有配置。
 * 同一枚举中的档位标识必须唯一，枚举常量初始化后可从任意线程读取。
 */
@NullMarked
public interface DifficultyLevel extends StringRepresentable {

    /**
     * 返回当前档位在配置中使用的标识。
     *
     * @return 非空白且在枚举常量初始化后保持固定的标识
     */
    @Override
    String getSerializedName();
}
