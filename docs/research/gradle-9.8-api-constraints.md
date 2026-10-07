# Gradle 9.8 插件编写的 API 约束

本仓（`krema-f-ext`）是 Gradle 9.8 Kotlin DSL 多模块插件工程。本文是**判词**：
哪些 Gradle 9.8 API 在本仓**禁止 / 不推荐 / 允许**，以及为什么。

所有结论附 `docs.gradle.org/9.8.0/...` 的出处 URL，均于 2026-10 实测在线，
不凭记忆。事实来源两类：**User Manual**（约束的规范表述）与 **Javadoc**（类型是否存在）。

> 判词 ≠ 检索。要「Gradle 9.8 全量 API 参考」请走生成的 Skill 制品
> （`java-docs-for-ai` 的 `crawl-gradle-plugin --version 9.8.0`），不落本仓正文。

## 1. 禁区总表

| 面 | 状态 | 本仓口径 |
|---|---|---|
| task action 里访问 `Project`（含 `Task.getProject()`） | **禁止**（CC 硬约束，非 CC 下也会成为 Gradle 10 错误） | 禁 |
| task action 里读 extension / convention / extra properties | **禁止**（非 CC 下也强制） | 禁 |
| task action 里查 `Task.getTaskDependencies()` 等依赖关系方法 | **9.8 起弃用**，Gradle 10 转错误 | 禁（配置期可用） |
| task 字段/action 引用自定义 `Collection`/`Map` 子类型 | **9.8 起弃用**，Gradle 10 转错误 | 禁 |
| `org.gradle.api.plugins.Convention` 类型 | **9.8 已移除**（Javadoc 404） | 禁 |
| `Project.getConvention()` | **9.8 已移除**（`Project` Javadoc 无此方法） | 禁 |
| Kotlin DSL 委托属性 `val x by prop` / `by extensions` | **9.8 起弃用移除中** | 禁 |
| 配置期起外部进程走 `project.exec{}` | CC 不友好 | 用 `ExecOperations` |
| task action 里做文件操作走 `project.copy{}` | CC 不友好 | 用 `@Inject FileSystemOperations` |
| 用 `providers` 惰性取值 | 推荐 | 优先 |
| `@Input`/`@InputFile`/`@OutputDirectory` 显式建模 | 推荐 | 优先 |

## 2. Configuration Cache 友好写法（本仓必须全绿）

CC 要求是**规范**，多数违规会被报为 CC problem 直接失败；少数（绕过字节码插桩的
配置期外部进程）**不报但结果错**。

https://docs.gradle.org/9.8.0/userguide/configuration_cache_requirements.html

### 2.1 不引用「活的」对象

- `Certain Types must not be Referenced by Tasks`：`Project`、`Gradle`、`Settings`、
  `SourceSet`、`Configuration` 等模型类型，以及 live JVM 状态、用户模块类，
  **不得作为 task 字段或 task action 的可达引用**。
  - 反例：`Project` 取 `project.version` —— 配置期取值，执行期用。
  - 正解：`Property<String>` 显式建模，`@Input` 标注。
    > Upgrade 指南原话：*"instead of referencing a `Project` to retrieve `project.version` at execution time, declare the project version as a `Property<String>` input"*。
- `Custom Collection and Map Types`：task 字段/action 引用**自定义** `Collection`/`Map`
  子类型，9.8 起**弃用**，Gradle 10 转错误。用标准类型（`ArrayList`/`LinkedHashMap`），
  额外数据放独立且显式序列化的字段。

### 2.2 不碰 extension / convention

`Accessing Task Extensions or Conventions`：**task 执行期不得访问 convention、
extension 或 extra properties**。执行期需要的值必须显式建模为 task property。

> 关键：*"This restriction is enforced even when the Configuration Cache is not
> enabled."* —— 即**不开 CC 也照禁**。本仓以此为硬线。

### 2.3 服务注入的正确组合

`Running External Processes` / `Injecting Services`：用 `@Inject` + `Service` 替代
`Project` 上的同名方法。

| 禁止（`Project` 方法） | 替代（注入服务） |
|---|---|
| `project.exec {}` | `ExecOperations.exec {}` |
| `project.javaexec {}` | `ExecOperations.javaexec {}` |
| `project.copy {}` | `FileSystemOperations.copy {}` |
| `project.delete {}` | `FileSystemOperations.delete {}` |
| `project.logger` | `Task.logger` |
| `project.ant {}` | `Task.ant` |

写法（本仓 task 类）：

```kotlin
abstract class SomeTask : DefaultTask() {
    @get:Input abstract val version: Property<String>
    @get:InputFile abstract val config: RegularFileProperty
    @get:OutputDirectory abstract val outDir: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations
    @get:Inject abstract val fs: FileSystemOperations

    @TaskAction
    fun action() {
        fs.copy { from(config); into(outDir) }
    }
}
```

`@Input` / `@InputFile` / `@OutputDirectory`（或对应的 `Property` / `RegularFileProperty` /
`DirectoryProperty`）是把「执行期要的值」钉死在配置期的唯一通道。

### 2.4 什么时候必须走 `providers` 而非现场取值

`Using External Information Sources`：

- **配置期**需要外部信息（git、CLI 探值、复杂系统属性）：用 `ValueSource`（可注入
  `ExecOperations`），而**不是**在 `build.gradle.kts` 里当场 `exec`。
- 需要进程输出的值：`providers.exec` / `providers.javaexec`，返回惰性 provider，
  优先**直接 wire 到 task 输入**，`get()` 只在万不得已时调。
- `BuildServiceProvider` **不能**驱动 `ValueSource`（`ValueSourceParameters` 里不能塞
  provider of BuildService）。

## 3. 9.8 相对 8.x 的实际变化（带出处）

### 3.1 task action 查依赖关系：9.8 起弃用

`Upgrading within Gradle 9.x.y` →
`Deprecation of accessing task dependency relationships from a task action`：

> Calling the following methods from a task action at execution time is now
> deprecated and will become an error in Gradle 10:
> `Task.getTaskDependencies()` / `Task.getDependsOn()` / `Task.getMustRunAfter()` /
> `Task.getFinalizedBy()` / `Task.getShouldRunAfter()`

- **配置期**仍可用。
- 弃用**仅在未开 CC 时**发出；开 CC 时这些调用被报为 CC problem。

https://docs.gradle.org/9.8.0/userguide/upgrading_version_9.html

本仓口径：**task action 里一律不查 `taskDependencies`**。工单原文点名的写法据此禁止。

### 3.2 `Convention` 类型与 `Project.getConvention()` 已移除

- `https://docs.gradle.org/9.8.0/javadoc/org/gradle/api/plugins/Convention.html` → **404**
- `https://docs.gradle.org/9.8.0/javadoc/org/gradle/api/Project.html` → `getConvention` **零命中**
- `allclasses-index.html` 中仅存 `SupportsConvention`（标记接口）与软件模型遗留类

结论：9.8 **没有** `Convention` 这个历史包袱了，不是「仍在但弃用」，是**已删**。
extension 用 `ExtensionContainer`（`org.gradle.api.plugins.ExtensionContainer`，Javadoc 在）。

### 3.3 Kotlin DSL 委托属性：移除中

`Upgrading within Gradle 9.x.y` 顶部（9.8 的 9.7→9.8 段）：

> Delegated properties are being removed because they create silent correctness bugs
> (e.g. no-op delegates, wrong extra property scope), couple entity names to variable
> names, diverge between build scripts and class-based plugins, and duplicate
> functionality already provided by explicit APIs.

受影响写法：`val x: String by myProperty`、`val ext: Type by extensions`。
替代：显式 `myProperty.set(...)` / `myProperty.get()`、`extensions.getByType<Type>()`。

本仓口径：**新代码不用委托属性**；既有的 `extensions.configure<...> {}` 不受影响。

## 4. java-toolchain 解析（foojay resolver）

出处：`https://docs.gradle.org/9.8.0/userguide/toolchains.html` →
*"One example of a toolchain resolver plugin is the Foojay Toolchains Plugin ... it even
has a convention variant, which automatically takes care of all the needed configuration,
just by being applied"*

本仓已在 `gradle/libs.versions.toml` 用 convention 变体，坐标与版本**照 9.8 文档**：

```toml
foojay-resolver = { id = "org.gradle.toolchains.foojay-resolver-convention", version = "1.0.0" }
```

```kotlin
// settings.gradle.kts
plugins { id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0") }
```

9.8 起 toolchain 支持到 **Java 27**（Gradle daemon 与 toolchain 均可），本仓目标
JDK 25 在支持范围内。

## 5. spotless 在 9.8 下的配置

Gradle 9.8 Release Notes 未提及 spotless 相关破坏性变更；本仓 `com.diffplug.spotless`
版本 `8.10.3` 与 Gradle 9.8 兼容（`./gradlew spotlessCheck` 在云构建门禁中已验证通过）。

**不需要**为 9.8 改 spotless 配置。若后续 spotless 升版引入 breaking，按
[spotless CHANGELOG](https://github.com/diffplug/spotless/blob/main/CHANGES.md) 对齐，
不预先加戏。

## 6. 事实来源清单

| 主题 | URL |
|---|---|
| CC 约束规范 | `https://docs.gradle.org/9.8.0/userguide/configuration_cache_requirements.html` |
| CC 总览 | `https://docs.gradle.org/9.8.0/userguide/configuration_cache.html` |
| 9.x 升级（弃用/破坏） | `https://docs.gradle.org/9.8.0/userguide/upgrading_version_9.html` |
| 8.x 升级（历史） | `https://docs.gradle.org/9.8.0/userguide/upgrading_version_8.html` |
| 9.8 Release Notes | `https://docs.gradle.org/9.8.0/release-notes.html` |
| Toolchains | `https://docs.gradle.org/9.8.0/userguide/toolchains.html` |
| `Project` Javadoc | `https://docs.gradle.org/9.8.0/javadoc/org/gradle/api/Project.html` |
| `Task` Javadoc | `https://docs.gradle.org/9.8.0/javadoc/org/gradle/api/Task.html` |
| `ExtensionContainer` Javadoc | `https://docs.gradle.org/9.8.0/javadoc/org/gradle/api/plugins/ExtensionContainer.html` |
| `Convention`（已移除，404） | `https://docs.gradle.org/9.8.0/javadoc/org/gradle/api/plugins/Convention.html` |
