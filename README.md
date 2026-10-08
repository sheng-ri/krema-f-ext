# krema-f-ext

把 [Krema](https://cnb.cool/BirthCat-Dev/krema-f) 的构建编排能力收进 Gradle 插件。

Krema 是一套 Java + 系统 WebView 的桌面框架，上游自带一个 `krema` CLI 来初始化项目、
跑开发服务器、构建与打包。本项目在它之上做二次开发：让 Gradle 拥有同一套构建编排能力。

## 模块

| 模块 | 职责 |
|---|---|
| `krema-gradle-plugin` | Gradle 插件薄壳：声明 task、绑定输入输出、实现 Gradle 差异 |
| `krema-cli-spi` | 中间层：Kotlin 编排契约与实现，不认识 Gradle 也不认识 Maven |

分层是为了职责清楚：插件管 Gradle 差异，中间层管编排，两边不互相认。

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
    configFile = layout.projectDirectory.file("krema.toml")

    // 产物根目录，默认 build/krema
    outputDir = layout.buildDirectory.dir("krema")

    // 主类，覆盖 krema.toml 的 [build].main_class
    mainClass = "com.example.app.Main"

    // [build] 一道缝：这里填的值覆盖 krema.toml 的同名项，不填则以 toml 为准
    frontend {
        buildCommand = "npm run build"
        devCommand = "npm run dev"
        devUrl = "http://localhost:5173"
    }
}
```

### task

以下 task 归属 `krema` 分组：

| task | 对应上游 CLI | 做什么 |
|---|---|---|
| `kremaInit` | init | 生成项目骨架（`krema.toml` + 主类 + `.gitignore`） |
| `kremaBuild` | build | 跑前端生产构建，再编译 `src-java` |
| `kremaBuildNative` | build --native | 常规编译之后打 native-image |
| `kremaBundleDev` | bundle | 生成开发用 bundle（只够原生特性认出应用身份） |
| `kremaBundleProd` | bundle | 生成可分发的生产 bundle，再把 classpath 与原生库放进去 |
| `kremaBundleDevRun` | — | 依赖 `kremaBundleDev`，跑 dev bundle，阻塞到窗口关 |
| `kremaBundleProdRun` | — | 依赖 `kremaBundleProd`，跑生产 bundle，阻塞到窗口关 |
| `kremaDev` | dev | 起前端 dev server 并编译，**起完即返回**，长驻，不进 CI 门禁 |
| `kremaSignerGenerate` | signer generate | 生成一对签名密钥（Ed25519） |
| `kremaSignerSign` | signer sign | 用私钥给产物签名 |

> `kremaDev` 与两张 `*Run` 的生命周期**故意不同**：`kremaDev` 起完即返回，`*Run` 阻塞
> `waitFor()`，Gradle 会话活到窗口关。别被「不一致」骗着去改齐。

#### bundle 的 `format` 与 `handshake`

- 四张 bundle task 都要求显式给 `format`（无默认值，不给即报错）。可选值取自
  `top.birthcat.kremaf.ext.spi.port.BundleFormat`，写错是**编译期**报错。
- `format` 按「装完能不能真跑」分族：`app-image` / `app` 可直接跑，安装器一族
  （`exe` / `msi` / `deb` / `rpm` / `dmg` / `pkg`）只能装完再跑。`*Run` 遇到安装器**硬报错**。
- `handshake` 是启动总闸门：默认 `inheritIO()`，终端里就是 app 输出；置 true 时捕获 stdout，
  判到标志行 `KREMA_APP_STARTED` 才继续，判完把输出接回终端。CI / 验证用。
