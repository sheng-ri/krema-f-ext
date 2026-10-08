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
| `kremaBundle` | bundle | 按平台生成可分发包（见下） |
| `kremaDev` | dev | 起前端 dev server 并编译，长驻，不进 CI 门禁 |
| `kremaSignerGenerate` | signer generate | 生成一对签名密钥（Ed25519） |
| `kremaSignerSign` | signer sign | 用私钥给产物签名 |

### 打包

`kremaBundle` 按 `target` 分派，产物落 `build/krema/<平台>/`：

| 平台 | 怎么打 | 产出 |
|---|---|---|
| `linux-*` | 真调 `jpackage --type deb` | `.deb` |
| `windows-*` | 真调 `jpackage --type exe` | 安装包目录（含 `.exe`） |
| `darwin-*` | 手搓 `.app` + ad-hoc codesign | `.app` |

macOS 不走 jpackage：手搓 `.app` 是 `kremaDev` 的硬依赖
（`requiresBundleForNativeFeatures`），交给 jpackage 重做会把 dev 流程带崩。

`target` 默认取当前平台，可覆盖：

```kotlin
tasks.named<top.birthcat.kremaf.ext.gradle.task.KremaBundleTask>("kremaBundle") {
    target.set("linux-x86_64")
}
```

jpackage 路径需要 JDK 自带的 `jpackage`；Linux 上打 `.deb` 另需 `fakeroot` 与 `binutils`。

**不做交叉打包**：`.exe` 只能在 Windows 打，`.deb` 只能在 Linux 打。
