# krema-f-ext

把 [Krema](https://cnb.cool/BirthCat-Dev/krema-f) 的构建编排能力收进 Gradle 插件。

Krema 是一套 Java + 系统 WebView 的桌面框架，上游自带一个 `krema` CLI 来初始化项目、
跑开发服务器、构建与打包。本项目在它之上做二次开发：让 Gradle 拥有同一套构建编排能力。

## 上游关系

编排能力在 `krema-cli-spi` 全自研，不依赖上游任何制品。上游仓库 `BirthCat-Dev/krema-f` 与
`build.krema:krema-core` 只作参考：要用的东西自己实现，自己负责。

本仓库用 Apache-2.0 许可。

## 模块

| 模块 | 职责 |
|---|---|
| `krema-gradle-plugin` | Gradle 插件薄壳：声明 task、绑定输入输出、实现 Gradle 差异 |
| `krema-cli-spi` | 中间层：Kotlin 编排契约与实现，不认识 Gradle 也不认识 Maven |

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
    id("top.birthcat.kremaf.ext")
}

krema {
    // krema.toml 位置，默认项目根下 krema.toml
    configFile.set(layout.projectDirectory.file("krema.toml"))

    // 产物根目录，默认 build/krema
    outputDir.set(layout.buildDirectory.dir("krema"))

    // [build] 一道缝：这里填的值覆盖 krema.toml 的同名项，不填则以 toml 为准
    frontendCommand.set("npm run build")
    frontendDevCommand.set("npm run dev")
    frontendDevUrl.set("http://localhost:5173")
    mainClass.set("com.example.app.Main")
}
```

`krema.toml` 管 Krema 语义（`[window]`/`[permissions]`/`[package]` 等），`krema { }` 只管构建语义。
判据是「谁比 toml 更懂」：上面六项 Gradle 更懂，其余 section 归 toml。

**除 `kremaInit` 外，所有 task 都要求 `krema.toml` 已存在**，缺失即硬报错。

### task

以下 task 归属 `krema` 分组：

| task | 对应上游 CLI | 做什么 |
|---|---|---|
| `kremaInit` | init | 生成项目骨架（`krema.toml` + 主类 + `.gitignore`）。唯一不要求 toml 先存在的 task |
| `kremaBuild` | build | 跑前端生产构建，再编译 `src-java` |
| `kremaBuildNative` | build --native | 常规编译之后打 native-image；工具链接通见 [#26](https://cnb.cool/BirthCat-Dev/krema-f-ext/-/issues/26) |
| `kremaBundle` | bundle | 按平台生成可分发包，再把 classpath 与原生库放进去 |
| `kremaDev` | dev | 起前端 dev server 并编译，长驻，不进 CI 门禁 |
| `kremaSignerGenerate` | signer generate | 生成一对签名密钥（Ed25519） |
| `kremaSignerSign` | signer sign | 用私钥给产物签名 |

```bash
./gradlew kremaInit
./gradlew kremaBuild
./gradlew kremaBundle            # 默认按当前平台
./gradlew kremaSignerGenerate
```

`kremaBundle` 的 `target`、`kremaSignerSign` 的 `artifact` / `privateKey` / `signature`
都声明为 Gradle `Property`，在 `build.gradle.kts` 里赋值，不走 `-P` 命令行。

task 的输入输出全为 `Property`，不注入 `Project`，Configuration Cache 干净。

## 依赖

插件与中间层发布到共享 maven registry：

- 页面：https://cnb.cool/BirthCat-Dev/jars
- 地址：`https://maven.cnb.cool/BirthCat-Dev/jars/-/packages/`
- 坐标：`top.birthcat.kremaf-ext:krema-gradle-plugin`、`top.birthcat.kremaf-ext:krema-cli-spi`

在 `settings.gradle.kts` 里加上仓库：

```kotlin
pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.cnb.cool/BirthCat-Dev/jars/-/packages/")
            credentials {
                username = "cnb"
                password = providers.gradleProperty("cnbArtifactsGradlePassword").getOrElse("")
            }
        }
    }
}
```

本地或流水线里取令牌：

```bash
# 本地
export cnbArtifactsGradlePassword=<你的访问令牌>
# 云构建/云开发内置
export cnbArtifactsGradlePassword=$CNB_TOKEN
```

## 发布

版本号取自构建脚本里的 `project.version`，发布形态有两种，都在 CNB 页面的分支详情页按钮触发：

| 形态 | 按钮 | 版本 | 说明 |
|---|---|---|---|
| Release | 发布 Release | 正式版本号 | 正式发布到 maven registry |
| Snapshot | 发布 Snapshot | `*-SNAPSHOT` | 快照发布，同版本号可覆盖 |

两个模块一起发布：`krema-gradle-plugin` 的 POM 依赖 `krema-cli-spi`，少发一个插件坐标就拉不通。

## 云构建

PR 与主干门禁跑 `./gradlew build spotlessCheck`。构建镜像在 `.cnb/build/Dockerfile`，
Gradle 依赖缓存在构建节点上复用。端到端窗口测试与真打安装包走手动入口。
