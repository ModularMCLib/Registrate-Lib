package com.modularmc.registrate.difficulty;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/**
 * 将配置里的档位标识解析为枚举常量，再按该常量取出业务参数。
 * 建表后可供多线程查询。表中保存参数对象的引用，可变对象的线程安全由调用方负责。
 *
 * @param <D> 模组声明的难度枚举
 * @param <V> 各档共用的参数类型
 */
@NullMarked
public final class DifficultyValues<D extends Enum<D> & DifficultyLevel, V> {

    private final Object2ObjectLinkedOpenHashMap<String, D> levelsById;
    private final EnumMap<D, V> valuesByLevel;

    /**
     * 复制档位与取值的映射。多个档位可以引用同一份值。
     *
     * @param levels 至少两个档位，通常由枚举的 {@code values()} 提供
     * @param values 每个已声明档位对应的非空值
     * @throws IllegalArgumentException 档位不足两个、标识为空白或重复，
     *                                  或缺少某个档位的值
     */
    public DifficultyValues(D[] levels, Map<D, ? extends V> values) {
        if (levels.length < 2) throw new IllegalArgumentException("At least two difficulty levels are required");

        levelsById = new Object2ObjectLinkedOpenHashMap<>(levels.length);
        valuesByLevel = new EnumMap<>(levels[0].getDeclaringClass());
        for (D level : levels) {
            String id = level.getSerializedName();
            if (id.isBlank()) throw new IllegalArgumentException("A difficulty ID is required for " + level.name());
            if (levelsById.putIfAbsent(id, level) != null) throw new IllegalArgumentException("Duplicate difficulty ID '" + id + "'");

            V value = values.get(level);
            if (value == null) throw new IllegalArgumentException("Missing value for difficulty '" + id + "'");
            valuesByLevel.put(level, value);
        }
    }

    /**
     * 按配置中的标识查找档位，区分大小写。
     *
     * @param id 从配置读取的档位标识
     * @return 与标识对应的枚举常量
     * @throws IllegalArgumentException 标识未声明，异常信息按声明顺序列出可用标识
     */
    public D level(String id) {
        D level = levelsById.get(id);
        if (level == null) throw new IllegalArgumentException("Unknown difficulty '" + id + "'. Available IDs " + levelsById.keySet());
        return level;
    }

    /**
     * 取得所选档位的值，供机器初始化或提示文本使用。
     *
     * @param level 选中的难度档位
     * @return 建表时为该档位提供的值
     * @throws IllegalArgumentException 建表时未声明该档位
     */
    public V value(D level) {
        V value = valuesByLevel.get(level);
        if (value == null) throw new IllegalArgumentException("Undeclared difficulty '" + level.getSerializedName() + "'");
        return value;
    }
}
