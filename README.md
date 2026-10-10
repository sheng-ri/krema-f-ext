# krema-f-ext

把 [Krema](https://cnb.cool/BirthCat-Dev/krema-f) 的构建编排能力收进 Gradle 插件。

Krema 是一套 Java + 系统 WebView 的桌面框架，上游自带一个 `krema` CLI 来初始化项目、
跑开发服务器、构建与打包。本项目在它之上做二次开发：让 Gradle 拥有同一套构建编排能力。

## 模块

| 模块 | 职责 |
|---|---|
| `krema-gradle-plugin` | Gradle 插件本体：声明 task、绑定输入输出、实现 Krema 的构建编排 |

## 构建

需要 JDK 25。Wrapper 已锁 Gradle 9.8，直接用即可。

```bash
./gradlew build          # 编译 + 测试 + 格式检查
./gradlew spotlessCheck  # 只跑格式检查
./gradlew spotlessApply  # 自动修格式
```

## 插件

在 `build.gradle.kts` 里应用插件，然后通过 `krema { }` 扩展配置。`krema { }` 是唯一的配置入口，
所有构建输入都写在这里；插件自己引入 JVM 基础能力，你不必在脚本里先写别的插件。

```kotlin
plugins {
    id("top.birthcat.kremaf.ext")
}

krema {
    // krema.toml 位置，声明出来的默认值是项目根下的 krema.toml。
    // 默认路径缺失即无值、不报错；显式指定却指向不存在的文件会硬失败并指到那个路径。
    configFile = layout.projectDirectory.file("krema.toml")

    // 产物根目录，默认 build/krema
    outputDir = layout.buildDirectory.dir("krema")

    // 主类，唯一来源；不写则回落到 krema.toml 的 [build].main_class，再不行用内建默认
    mainClass = "com.example.app.Main"

    // 前端输入：这里填的值压过 krema.toml 的同名项，不填则以 toml 为准
    frontend {
        buildCommand = "npm run build"
        devCommand = "npm run dev"
        devUrl = "http://localhost:5173"
    }
}
```

源码放在 Gradle 认得的标准主 source set（`src/main/java` 或 `src/main/kotlin`），编译产物来自 Gradle 的
`main` source set 与 `runtimeClasspath`，插件不假设编译方言，也不认 `src-java` 这类自造目录约定。

### task

以下 task 归属 `krema` 分组：

| task | 对应上游 CLI | 做什么 |
|---|---|---|
| `kremaInit` | init | 生成项目骨架（`krema.toml` + 主类 + `.gitignore`），主类落在 `src/main/java` |
| `kremaBuild` | build | 让 Gradle 编译主 source set，并跑前端生产构建（`frontend.buildCommand`），把 `dist` 作为可缓存、UP-TO-DATE 的产出收进 `build/krema` |
| `kremaBundleDev` | bundle | dev 档打包：最小形态（macOS 才给正经 `.app`，其余平台靠 devUrl 拉前端），不打包 `dist`，产物落 `build/krema/dev` |
| `kremaBundleProd` | bundle | prod 档打包：全平台出完整可分发形态，并把 `dist` 收进去，产物落 `build/krema/prod` |
| `kremaBundleRunDev` | dev | 起前端 dev server 并跑 dev 档应用，长驻前台，中断时回收进程，不进 CI 门禁 |
| `kremaBundleRunProd` | dev | 跑 prod 档应用，长驻前台，中断时回收进程，不进 CI 门禁 |
| `kremaSignerGenerate` | signer generate | 生成一对签名密钥（Ed25519） |
| `kremaSignerSign` | signer sign | 用私钥给产物签名 |

打包建立在 `kremaBuild` 之上（先编译、再收前端），dev 与 prod 各占独立的产物目录互不干扰。
签名与 native-image 不在本骨架范围内（见对应上游票）。
