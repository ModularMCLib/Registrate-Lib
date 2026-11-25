package com.modularmc.registrate;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ModularMCLib 维护的 Registrate 运行时入口。
 *
 * <p>
 * 下游模组可将此库作为独立的 NeoForge 模组依赖。集成用例、注册示例和视觉检查入口位于 {@code test/src/main}。
 * </p>
 */
@Mod(RegistrateLib.MOD_ID)
public final class RegistrateLib {

    public static final String MOD_ID = "registratelib";
    public static final String MOD_NAME = "Registrate Lib";

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    /**
     * 供 NeoForge 加载运行时库时实例化。各下游模组自行创建其所属的 {@link Registrate}。
     *
     * @param modEventBus NeoForge 注入的本库模组事件总线
     */
    public RegistrateLib(IEventBus modEventBus) {}
}
