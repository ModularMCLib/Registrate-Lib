# 1.21.1 本地化与迁移

## 配置源语言

为每个生成语言数据的模组设置 `MinecraftLocale`。把原有 `.lang(String)` 和 `addRawLang` 文本实际使用的语言设为源语言。`defaultLang()` 从注册路径生成英文名称，适用于 `EN_US`。其他源语言的对象可用 `.lang(String)` 或 `.localization(...)` 声明源文本。

```java
import com.modularmc.registrate.Registrate;
import com.modularmc.registrate.localization.MinecraftLocale;
import java.nio.file.Path;

Registrate registrate = Registrate.create(MOD_ID).sourceLocale(MinecraftLocale.EN_US);
registrate.export().localization()
        .path(Path.of("build", "localization", "mymod.json"))
        .build();
```

`path` is the destination JSON file. Relative paths resolve against the DataGen process working directory. The export request is fulfilled after language callbacks have run once; the same declarations produce both Minecraft language files and the exchange document.

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

DataGen 默认从已解析的 `zh_cn` 译文派生 `zh_tw`（台灣）和 `zh_hk`（香港）。转换先用 ICU 做简转繁，再应用 OpenCC 的地区字形规则。`zh_tw` 还应用台灣用词，例如将「软件」转换为「軟體」。

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

## 注解声明

添加 RegistrateLib 模组依赖即可在 Java 和 Kotlin 源码中使用本地化注解。DataGen 从 NeoForge 的扫描结果读取所属模组文件中的声明。升级时移除 RegistrateLib 的 `annotationProcessor` 和 `ksp` 依赖。旧构建目录里有索引文件时，清理一次再构建。

`@Trans` 和 `@TransVariants` 可以声明在类型或字段上。字段的 `modId` 优先使用显式值，省略时继承声明类上 `@Trans` 或 `@TransVariants` 的 `modId`，与取键方式无关。类上的两个本地化注解指定不同的 `modId` 时，字段需要显式指定。没有可继承的值时，单 Mod 文件默认归属于该 Mod，多 Mod 文件需要显式指定。DataGen 使用最终所属 Mod 的源 Locale。

使用类级前缀的字段也可以继承类上的 `namespace`，字段可以单独指定。省略 `namespace` 且没有继承值时，资源命名空间取所属 Mod 的 ID。

在类上声明一次前缀，字段只需填写源文本和可选译文。默认按 JVM 字段名拼接，如 `gui.mymod.power.standby`。设置 `useFieldValues = true` 后，同组字段改用字符串常量值作后缀，如 `gui.mymod.tier.lv`。

```java
@TransVariants(keyPrefix = "gui.mymod.power")
final class PowerLabels {
    @Trans(source = "Standby")
    static final String standby = "gui.mymod.power.standby";
}

@TransVariants(keyPrefix = "gui.mymod.tier", useFieldValues = true)
final class TierLabels {
    @Trans(source = "Low voltage")
    static final String LOW_VOLTAGE = "lv";
}
```

字段的取键顺序为显式 `key`、`namePrefix` 加字段名、`valuePrefix` 加字段值、声明该字段的类上的前缀、字段值本身。`@Trans(valuePrefix = "gui.mymod.tier", source = "Low voltage")` 可以单独用于值为 `"lv"` 的字段。按值取键要求 Java 编译期 `static final String` 常量或 Kotlin `const val`。类型上的 `@Trans` 需要显式 `key`。

也可以在 `@TransVariants` 的 `value` 数组中直接列出变体。

```java
@TransVariants(keyPrefix = "gui.mymod.power", value = {
        @Variant(id = "low", source = "Low power"),
        @Variant(id = "high", source = "High power")
})
final class Labels {
    @Trans(key = "gui.mymod.container", source = "Test Container",
            translations = @Translation(locale = MinecraftLocale.ZH_CN, text = "测试容器"))
    static final String CONTAINER = "gui.mymod.container";
}
```

Kotlin 中在带有后备字段的属性上使用 `@field:Trans` 或 `@field:TransVariants`。变体的 Key 由前缀和 ID 拼接，例如 `gui.mymod.power.low`。同一命名空间、Key 和 Locale 下，同级异值声明会报告双方来源，并用类名、字段名和变体 ID 定位注解。`%s` 的隐式索引按出现顺序分配，译文可用 `%1$s` 等显式索引重排。

## 注册兼容性

- `RegistryEntry.metadata()` 提供条目的本地化声明和 DataGen 回调视图。读取本地化声明所引用的原生 Key 时，目标 Holder 需要已绑定。
- 已有样例沿用原注册 ID 和原生翻译 Key。BlockItem 复用方块名称时，复用同一个 Key。
- 混合源码项目可在 Java 的 `@Mod` 入口创建 `Registrate` 并把它传给 Kotlin 注册类。测试模组用同一实例注册 `testmod:magic_item_model`，GameTest 从 Java 入口的实例查找该方块，并核对 BlockItem 的 `block.testmod.magic_item_model`。
- 同一个 Registrate 实例内，同一注册表的重复 ID 会在提交时失败。不同注册表可以使用相同的名称。
