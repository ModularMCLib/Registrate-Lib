# 26.1 本地化与迁移

## 配置源语言

为每个生成语言数据的模组设置 `MinecraftLocale`。把原有 `.lang(String)` 和 `addRawLang` 文本实际使用的语言设为源语言。`defaultLang()` 从注册路径生成英文名称，适用于 `EN_US`。其他源语言的对象可用 `.lang(String)` 或 `.localization(...)` 声明源文本。

```java
import com.modularmc.registrate.Registrate;
import com.modularmc.registrate.localization.MinecraftLocale;

Registrate registrate = Registrate.create(MOD_ID).sourceLocale(MinecraftLocale.EN_US);
registrate.export().localization().build();
```

The export chain registers a DataGen output request. The language callbacks run once, then the same collected declarations produce both Minecraft language files and `data/<modid>/registrate/localization.json`.

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

在 `registrate.terminology()` 中登记 `Domain`、`GlossaryTerm` 和 `MeasurementUnit`。用 `.localizationDefaults(defaults)` 设置模组级默认值，Builder 的 `.defaults(defaults)` 可设置条目级默认值。条目的显式参数覆盖对应默认值。

### 按语境选取术语

词表按声明所属的模组查询。完整源文本与词条匹配时，优先使用 `.glossaryIds("electric_potential")` 或 `.glossary(term)` 指定的术语。其余候选按 `Domain` 和 `TextType` 筛选，匹配程度相同的词条会报告声明位置与术语 ID。词表的首选译名可以补充缺少的译文。条目已有译文时，解析保留显式值，并提示禁用译名或词表未收录的写法。

通过 `registrate.terminology().register(glossary)` 接入项目自己的 `Glossary` 实现。接口通过 `find(id)` 查单条定义，通过 `findBySource(normalizedSource)` 返回完整标签的所有词义。源文本查询使用与 Locale 无关的小写形式。同一模组内的词条 ID 对应唯一的定义。

词条可登记缩写和别名、用 `TermRelation` 指定与其他词条的关系，以及关联已登记的单位 ID。单位通过 `MeasurementUnit.format(value, locale)` 格式化数值和符号。`UnitFormat` 配置 `DecimalFormat` 模式、小数分隔符和数值与符号之间的间距。

`DeclarationDefaults.Builder` 依次应用基础默认值、继承值和组合值，对应 `defaults`、`inherits` 和 `include`。同层的集合值可合并，冲突的文本类型或同 ID 词条会报错。声明可用 `.units("ohm")`、`.glossaryIds(...)` 等显式字段覆盖对应的默认值。

### 语言转换与回退

`registrate.deriveLocale(new LocaleDerivation(EN_US, EN_GB, converter))` 为模组注册转换规则。转换器处理未受保护的文本片段，可读取语言、Key 和声明。`LocaleDerivation.fallback(EN_GB, EN_AU)` 在缺少目标译文时使用已解析的输入语言。规则允许组成多步路径，循环会在 DataGen 收集时报告。已有译文优先于转换和回退。

转换会保留 Minecraft 参数、样式标记、URL、换行和已声明的单位符号。默认的 `en_us → en_ud` 同样遵循这些规则，并用显式索引保持倒置后的参数含义。

### 导出交换数据

导出文档的 `formatVersion` 当前为 `1`。每个条目保留输出命名空间、原始 Key、源文本、SHA-256 源修订、领域、TextType、词条与单位引用、占位符签名和译文候选。译文记录包含 Locale、文本、来源类型与位置、源修订、审核状态和锁定标记。自动 Locale 变体也会进入记录，`source.location` 标明原始声明与转换路径。

JSON Schema 位于 [`localization-exchange.schema.json`](localization-exchange.schema.json)。条目按命名空间和 Key 排序，译文按 Locale、来源类型和来源位置排序。显式译文的 `status` 为 `null`；导入译文保留当前审核状态和其审核时使用的源修订。

### 导入人工译文

在模组初始化时用 `importTranslation` 登记已读入的译文。提供译者审核时对应的源文本修订，以及可以定位到文件或平台条目的来源。

```java
import com.modularmc.registrate.localization.ImportedTranslation;
import com.modularmc.registrate.localization.MinecraftLocale;
import com.modularmc.registrate.localization.SourceRevision;
import com.modularmc.registrate.localization.TranslationKey;
import com.modularmc.registrate.localization.TranslationStatus;

registrate.importTranslation(new ImportedTranslation(
        new TranslationKey(MOD_ID, "item.mymod.copper_ingot"),
        MinecraftLocale.ZH_CN,
        "铜锭",
        SourceRevision.of("Copper Ingot"),
        TranslationStatus.APPROVED,
        false,
        "translations/zh_cn.json"));
```

DataGen 用模组当前选中的源文本核对修订。源文本变化会将译文标为 `STALE`。锁定译文仍可参与选择，未锁定的过期译文仅用于诊断，此时配置的术语、转换和回退规则可以继续解析目标 Locale。开发者显式声明优先，其次是锁定的导入译文和当前修订的普通导入译文。同一层级中，已批准、审核中和草稿依次参与选择。

## 接入注解处理器

用 `./gradlew :localization-processor:jar` 构建与库版本对应的处理器。Java 源码使用 `annotationProcessor`，Kotlin 源码使用 KSP 插件和 `ksp`。混合项目可同时使用两项配置，主库作为模组依赖。

```groovy
dependencies {
    def processor = files("libs/registratelib-26.1.2-${registratelib_version}-localization-processor.jar")
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

## 从 1.21.1 迁移到 26.1

- 资源 ID 使用 `Identifier`，通过 `ResourceKey.identifier()` 读取注册位置。物品与方块 Builder 会给构造属性设置注册 Key。
- 方块着色使用 `.color(() -> () -> List.of(BlockTintSources.constant(0xFFFF0000)))`，物品着色在模型回调中声明。流体纹理通过 `.model(still, flowing)` 或 `FluidModel.Unbaked` 声明。
- `.spawnEgg(egg -> egg.model(...))` 使用 26.1 的刷怪蛋组件和物品模型。渲染器采用独立的 RenderState，实体创建通过 `EntityEntry.create(level, reason)` 指定 `EntitySpawnReason`。
- 配方继续通过 `ProviderType.RECIPE` 添加回调。烹饪配方显式接收 `CookingBookCategory`，标签原料使用 `DataIngredient.tag(HolderSet.Named<Item>)`。
