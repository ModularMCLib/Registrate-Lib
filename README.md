<h1 align="center">RegistrateLib</h1>

<p align="center">
  用于创建和注册模组内容的 NeoForge 库。<br>
  A NeoForge library for creating and registering mod content.
</p>

<p align="center">
  <a href="https://github.com/ModularMCLib/Registrate-Lib/releases"><img src="https://img.shields.io/badge/GitHub-Releases-24292F?logo=github&amp;logoColor=white&amp;style=for-the-badge" alt="GitHub Releases"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MPL--2.0-blue?style=for-the-badge" alt="License"></a>
  <a href="https://discord.gg/HwnF3RTPtH"><img src="https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&amp;logoColor=white&amp;style=for-the-badge" alt="Discord"></a>
</p>

---

## 分支与版本 / Branches and Versions

| 分支 / Branch | Minecraft | NeoForge | Java | CI | 最新 Maven / Latest Maven | 下载 / Download |
| --- | --- | --- | --- | --- | --- | --- |
| [`26.1`](https://github.com/ModularMCLib/Registrate-Lib/tree/26.1) | `26.1.2` | `26.1.2.126` | `25` | [![CI (26.1)](https://img.shields.io/github/actions/workflow/status/ModularMCLib/Registrate-Lib/auto-build.yml?branch=26.1&style=flat-square)](https://github.com/ModularMCLib/Registrate-Lib/actions/workflows/auto-build.yml?query=branch%3A26.1) | [![Latest Maven version](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.fury.io%2Fmodularmclib%2Fcom%2Fmodularmc%2Fregistrate%2Fregistratelib-26.1.2%2Fmaven-metadata.xml&style=flat-square)](https://maven.fury.io/modularmclib/com/modularmc/registrate/registratelib-26.1.2/maven-metadata.xml) | [开发快照 / Snapshot](https://github.com/ModularMCLib/Registrate-Lib/releases/tag/latest-26.1) |
| [`1.21`](https://github.com/ModularMCLib/Registrate-Lib/tree/1.21) | `1.21.1` | `21.1.265` | `21` | [![CI (1.21)](https://img.shields.io/github/actions/workflow/status/ModularMCLib/Registrate-Lib/auto-build.yml?branch=1.21&style=flat-square)](https://github.com/ModularMCLib/Registrate-Lib/actions/workflows/auto-build.yml?query=branch%3A1.21) | [![Latest Maven version](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.fury.io%2Fmodularmclib%2Fcom%2Fmodularmc%2Fregistrate%2Fregistratelib-1.21.1%2Fmaven-metadata.xml&style=flat-square)](https://maven.fury.io/modularmclib/com/modularmc/registrate/registratelib-1.21.1/maven-metadata.xml) | [开发快照 / Snapshot](https://github.com/ModularMCLib/Registrate-Lib/releases/tag/latest-1.21) |

Maven 徽章显示 [ModularMCLib Maven](https://maven.fury.io/modularmclib/) 为对应 Minecraft 版本公开的最新版本。带 `-SNAPSHOT` 的版本是开发快照。

The Maven badges show the latest published versions for each Minecraft version in [ModularMCLib Maven](https://maven.fury.io/modularmclib/). Versions ending in `-SNAPSHOT` are development builds.

本仓库是 ModularMCLib 维护的 Registrate 分支，包命名空间为 `com.modularmc.registrate`。

This is the ModularMCLib fork of Registrate, using the `com.modularmc.registrate` package namespace.

---

## 快速链接 / Quick Links

<!-- - [Javadocs](https://ci.tterrag.com/job/Registrate/job/1.21/javadoc/) -->
- Javadocs（待定 / TBD）
- [维基 / Wiki](https://github.com/ModularMCLib/RegistrateLib/wiki)
- [贡献指南 / Contributing](CONTRIBUTING.md)

---

## 为什么使用 Registrate? / Why Registrate?

- 你可以按照自己的方式组织模组内容，无需将每个对象的定义分散在各处
  Allows you to organize your mod content however you like, rather than having pieces of each object defined in scattered places
- 简洁的流式 API / Simple fluent API
- 可扩展，支持构建和注册自定义对象及数据
  Open to extension, build and register custom objects and data
- 自动数据生成，提供合理的默认值
  Automatic data generation with sane defaults
- 既可作为独立的 NeoForge 库模组使用，也可作为下游模组的声明依赖使用
  Usable as a standalone NeoForge library mod and as a declared dependency for downstream mods

---

## 代码分类 / Code Classification

- `com.modularmc.registrate` — 公开 API 入口、最小化的库引导、注册协调中心 / public API roots, minimal library bootstrap, and the central registration coordinator
- `com.modularmc.registrate.builders` — 下游模组用于注册内容的流式 DSL / the fluent DSL used by downstream mods to register content
- `com.modularmc.registrate.providers` — 数据生成编排与提供者生命周期管理 / data generation orchestration and provider lifecycle management
- `com.modularmc.registrate.providers.generators` — 方块状态、模型和配方生成器适配器 / blockstate, model, and recipe generator adapters
- `com.modularmc.registrate.providers.loot` — 战利品表提供者封装 / loot-table focused provider wrappers
- `com.modularmc.registrate.util` — 公开的共享辅助工具 / public shared helpers that remain part of the reusable library surface
- `com.modularmc.registrate.util.entry` — 构建器返回的强类型注册句柄 / strongly-typed registry handles returned by builders
- `com.modularmc.registrate.util.nullness` — 空安全的函数式辅助与包级默认值 / null-safe functional helpers and package defaults
- `com.modularmc.registrate.internal` — 核心运行时的非 API 实现细节 / non-API implementation details extracted from the core runtime
- `com.modularmc.registrate.internal.event` — 用于构建器和注册生命周期钩子的一次性事件连接 / one-shot event wiring used by builders and registration lifecycle hooks
- `com.modularmc.registrate.internal.lifecycle` — 实例级生命周期桥接 / per-instance lifecycle bridges
- `com.modularmc.registrate.internal.util` — 分发限制执行与内部日志辅助 / dist-gated execution and internal logging helpers
- `com.modularmc.registratetest.mod` — 集成测试示例模组 / the integration-style sample mod
- `com.modularmc.registratetest.gametests` — 可执行的运行时验证场景 / executable validation scenarios for runtime behavior
- `com.modularmc.registratetest.meta` — 维护工具，使生成的桥接方法与上游 API 保持一致 / maintenance utilities that keep generated bridge methods aligned with upstream APIs

---

## 使用方法 / How to Use

首先，创建一个 `Registrate` 对象，该对象将在整个项目中使用。  
First, create a `Registrate` object which will be used across your entire project.

```java
public static final Registrate REGISTRATE = Registrate.create(MOD_ID).sourceLocale(MinecraftLocale.EN_US);
```

不一定要使用常量字段，可以在注册设置完成后传递或丢弃。  
Using a constant field is not necessary, it can be passed around and thrown away after registration is setup.

如果在 `@Mod` 类中声明为 `static`，必须延迟创建 `Registrate` 对象，避免在加载过程中过早创建。可以这样轻松实现：  
If declared static in your `@Mod` class, you must create the `Registrate` object lazily so it is not created too early during loading. This can be done easily like so:

```java
public static final NonNullSupplier<Registrate> REGISTRATE =
        NonNullSupplier.lazy(() -> Registrate.create(MOD_ID).sourceLocale(MinecraftLocale.EN_US));
```

接下来，开始添加对象。  
Next, begin adding objects.

如果你有一个方块类：  
If you have a block class such as:

```java
public class MyBlock extends Block {

    public MyBlock(Block.Properties properties) {
        super(properties);
    }

    ...
}
```

可以这样注册：  
then register it like so:

```java
public static final RegistryEntry<MyBlock> MY_BLOCK = REGISTRATE.block("my_block", MyBlock::new).register();
```

Registrate 会创建一个方块，并生成默认的简单方块状态、模型、战利品表和语言条目。所有这些方面都可以轻松配置为自定义数据。例如：  
Registrate will create a block, with a default simple blockstate, model, loot table, and lang entry. However, all of these facets can be configured easily to use whatever custom data you may want. Example:

```java
public static final BlockEntry<MyStairsBlock> MY_STAIRS = REGISTRATE.block("my_stairs", MyStairsBlock::new)
            .simpleItem()
            .tag(BlockTags.STAIRS)
            .blockstate(() -> (ctx, prov) -> prov.stairsBlock(ctx.getEntry(), prov.modBlockTexture(ctx.getName())))
            .lang("Special Stairs")
            .register();
```

这个自定义版本会创建 BlockItem（含默认模型和语言条目）、将方块添加到标签、配置楼梯属性方块状态，并添加自定义本地化。  
This customized version will create a BlockItem (with its own default model and lang entry), add the block to a tag, configure the blockstate for stair properties, and add a custom localization.

源语言、译文和注解处理器的接入方式见 [本地化与迁移](docs/localization.md)。

<!-- 有关不同 API 和方法的概述，请查阅 [Javadocs](https://ci.tterrag.com/job/Registrate/job/1.21/javadoc/)。 -->
有关不同 API 和方法的概述，请查阅 Javadocs（待定）。更高级的用法请阅读 [维基](https://github.com/ModularMCLib/RegistrateLib/wiki)（编写中）。  
<!-- To get an overview of the different APIs and methods, check out the [Javadocs](https://ci.tterrag.com/job/Registrate/job/1.21/javadoc/). -->
To get an overview of the different APIs and methods, check out the Javadocs (TBD). For more advanced usage, read the [wiki](https://github.com/ModularMCLib/RegistrateLib/wiki) (WIP).

---

## 项目设置 / Project Setup

将库添加到 Gradle 依赖：  
Add the library to your Gradle dependencies:

```groovy
dependencies {
    implementation "com.modularmc.registrate:registratelib-${minecraft_version}:${registratelib_version}"
}
```

将 `minecraft_version` 和 `registratelib_version` 设为上表同一行的 Minecraft 和 Maven 版本。

Set `minecraft_version` and `registratelib_version` to the Minecraft and Maven versions from the same row above.

然后在 `neoforge.mods.toml` 中将 `registratelib` 声明为必需依赖：  
Then declare `registratelib` as a required dependency in your `neoforge.mods.toml`:

```toml
[[dependencies.yourmodid]]
modId="registratelib"
type="required"
versionRange="[2.0.0,)"
ordering="AFTER"
side="BOTH"
```

---

## 开发指南 / Development

- 使用 `Java 25` 进行本地构建和 IDE 同步 / Use `Java 25` for local builds and IDE sync
- 优先使用捆绑的 IDEA 运行配置：`Client`、`Server`、`Data Generation`、`Game Tests`、`Game Tests (Client)`
  Prefer the bundled IDEA run configurations
- 流式注册 API 放在 `builders`，数据生成编排放在 `providers`，公开辅助工具放在 `util`，实现细节放在 `internal`
  Keep fluent registration APIs in `builders`, data generation orchestration in `providers`, public reusable helpers in `util`, and implementation plumbing in `internal`
- 查看 [`docs/architecture.md`](docs/architecture.md) 了解包职责和扩展点
  See [`docs/architecture.md`](docs/architecture.md) for package responsibilities and extension points

---

## 贡献 / Contributing

请阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md) 了解如何为此项目做出贡献。  
Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) for details on how to contribute to this project.
