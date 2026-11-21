# 1.21.1 本地化与迁移

## 配置源语言

为每个生成语言数据的模组设置 `MinecraftLocale`。把原有 `.lang(String)` 和 `addRawLang` 文本实际使用的语言设为源语言。`defaultLang()` 从注册路径生成英文名称，适用于 `EN_US`。其他源语言的对象可用 `.lang(String)` 或 `.localization(...)` 声明源文本。

```java
import com.modularmc.registrate.Registrate;
import com.modularmc.registrate.localization.MinecraftLocale;

Registrate registrate = Registrate.create(MOD_ID).sourceLocale(MinecraftLocale.EN_US);
```

## 声明文本

物品、方块、实体和流体的 Builder 从注册对象取 Minecraft 原生翻译 Key。通用 Builder 可用 `.localization(keyFunction, text -> ...)` 指定取 Key 的方式。

```java
registrate.item("copper_ingot", Item::new)
        .localization(text -> text
                .source("Copper Ingot")
                .domain(Domain.MATERIALS)
                .type(TextType.MATERIAL)
                .translation(MinecraftLocale.ZH_CN, "铜锭"))
        .register();

var title = registrate.addLocalization(new TranslationKey(MOD_ID, "gui.mymod.voltage"),
        text -> text.source("Voltage: %s V").translation(MinecraftLocale.ZH_CN, "电压：%1$s V"));
```

`addLocalization` 返回可用于 GUI 的翻译组件。进度标题等跨侧文本可在模组构造期间声明，在服务端 DataGen 的进度回调中引用组件。`addLang`、`addRawLang` 和 `ProviderType.LANG` 回调都使用同一个源 Locale。

在 `registrate.localizationCatalog()` 中登记 `Domain`、`GlossaryTerm` 和 `Unit`。Builder 的 `.profile(profile)` 可复用领域、文本类型和术语。条目的显式参数覆盖 Profile 的相应字段。

## 接入注解处理器

用 `./gradlew :localization-processor:jar` 构建与库版本对应的处理器。Java 源码使用 `annotationProcessor`，Kotlin 源码使用 KSP 插件和 `ksp`。混合项目可同时使用两项配置，主库作为模组依赖。

```groovy
dependencies {
    def processor = files("libs/registratelib-1.21.1-${registratelib_version}-localization-processor.jar")
    annotationProcessor(processor)
    ksp(processor)
}
```

`@Trans` 和 `@TransVariants` 可以声明在类型或字段上。`modId` 选择声明归属的模组和源 Locale。省略 `namespace` 时，资源命名空间取 `modId`。

```java
@TransVariants(modId = "mymod", keyPrefix = "gui.mymod.power", value = {
        @Variant(id = "low", source = "Low power"),
        @Variant(id = "high", source = "High power")
})
final class Labels {
    @Trans(modId = "mymod", key = "gui.mymod.container", source = "Test Container",
            translations = @Translation(locale = MinecraftLocale.ZH_CN, text = "测试容器"))
    static final String CONTAINER = "gui.mymod.container";
}
```

Kotlin 中在带有后备字段的属性上使用 `@field:Trans` 或 `@field:TransVariants`。两个处理器按 Mod ID 写入各自的版本化声明索引，DataGen 从所属模组读取并合并。变体的 Key 由前缀和 ID 拼接，例如 `gui.mymod.power.low`。同一命名空间、Key 和 Locale 下，同级异值声明会报告双方来源。`%s` 的隐式索引按出现顺序分配，译文可用 `%1$s` 等显式索引重排。

## 注册兼容性

- `RegistryEntry.metadata()` 提供条目的本地化声明和 DataGen 回调视图。读取本地化声明所引用的原生 Key 时，目标 Holder 需要已绑定。
- 已有样例沿用原注册 ID 和原生翻译 Key。BlockItem 复用方块名称时，复用同一个 Key。
- 混合源码项目可在 Java 的 `@Mod` 入口创建 `Registrate` 并把它传给 Kotlin 注册类。测试模组用同一实例注册 `testmod:magic_item_model`，GameTest 从 Java 入口的实例查找该方块，并核对 BlockItem 的 `block.testmod.magic_item_model`。
- 同一个 Registrate 实例内，同一注册表的重复 ID 会在提交时失败。不同注册表可以使用相同的名称。
