# krema-f-ext

把 [Krema](https://cnb.cool/BirthCat-Dev/krema-f) 的构建编排能力收进 Gradle 插件。

Krema 是一套 Java + 系统 WebView 的桌面框架，上游自带一个 `krema` CLI 来初始化项目、
跑开发服务器、构建与打包。本项目不改上游一行，而是在它之上做二次开发：让 Gradle 拥有
同一套构建编排能力。

## 上游关系

- 上游仓库 `BirthCat-Dev/krema-f` 是只读参照。
- 运行时依赖走 Maven Central 的 `build.krema:krema-core`，不依赖上游源码。
- 本仓库用 Apache-2.0 许可，`NOTICE` 里留了与上游的关系说明。

## 模块

| 模块 | 职责 |
|---|---|
| `krema-gradle-plugin` | Gradle 插件薄壳：声明 task、绑定输入输出、实现 Gradle 差异 |
| `krema-cli-spi` | 中间层：纯 Java 的编排契约与实现，不认识 Gradle 也不认识 Maven |

分层的意义：编排逻辑在不起 Gradle 的情况下就能单测。

## 构建

需要 JDK 25。Wrapper 已锁 Gradle 9.8，直接用即可。

```bash
./gradlew build          # 编译 + 测试 + 格式检查
./gradlew spotlessCheck  # 只跑格式检查
./gradlew spotlessApply  # 自动修格式
```

## 插件

在 `build.gradle.kts` 里应用插件，然后通过 `krema { }` 扩展配置：

```kotlin
plugins {
    id("cnb.cool.birthcat.krema")
}

krema {
    // 配置项随各 task 落地补齐
}
```

### task

以下 task 归属 `krema` 分组，选项通过 Gradle `Property` 暴露（名字随落地补齐）：

| task | 对应上游 CLI |
|---|---|
| `kremaInit` | init |
| `kremaBuild` | build |
| `kremaBuildNative` | build --native |
| `kremaBundle` | bundle |
| `kremaDev` | dev |
| `kremaSignerGenerate` | signer generate |
| `kremaSignerSign` | signer sign |

> 骨架阶段这些 task 尚未实现，用法随对应工单补齐。
