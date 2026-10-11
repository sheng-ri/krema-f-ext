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
        // 前端工程目录，相对项目根；不写就是项目根
        dir = "front"
        buildCommand = "npm run build"
    }

    // 运行时镜像要打进哪些 JDK 模块。不写就让 jdeps 按应用 classpath 推导，
    // 推导不出来时（反射、运行时才加载的类）才需要在这里显式补上
    runtime {
        modules = setOf("java.base", "java.desktop")
    }
}
```

### task

以下 task 归属 `krema` 分组：

| task | 对应上游 CLI | 做什么 |
|---|---|---|
| `kremaInit` | init | 生成项目骨架（`krema.toml` + 主类 + `.gitignore`），主类落在 `src/main/java` |
| `kremaBuild` | build | 聚合后端与前端：让 Gradle 编译主 source set，跑前端生产构建（`frontend.buildCommand`），把后端 app jar 与独立的前端 assets jar 作为可缓存、UP-TO-DATE 的产出收进 `build/krema/lib` |
| `kremaFrontendBuild` | — | 跑前端生产构建（`frontend.buildCommand`），产物目录取 `krema.toml` 的 `out_dir`（默认 `dist/`，落在前端目录下）声明成可缓存、UP-TO-DATE 的输出；被 `kremaBuild` 依赖，也可单独跑只重做前端 |
| `kremaRuntimeImage` | — | 用 jdeps 推导模块、jlink 裁出最小运行时镜像，产物落 `build/krema/runtime`；`runtime.modules` 非空时改用你给的清单 |
| `jdepsModules` | — | 打印 jdeps 推导出的运行时模块清单，不裁镜像。想手动指定模块前先跑它看清推导结果 |
| `jdepsModulesVerbose` | — | 打印 `jdeps -v` 的逐包依赖明细，并把原始输出写到 `build/krema/jdeps-verbose.txt` 供事后分析 |
| `kremaBundleDist` | bundle | 便携包：jpackage 产出的 app-image 目录外加一份 `<app>-<version>-<target>.zip`，产物落 `build/krema/dist` |
| `kremaBundlePlatform` | bundle | 平台包：本平台原生的可安装件底座（各格式在别处接上），产物落 `build/krema/platform` |
| `kremaBundleRun` | — | 跑应用，长驻前台，中断时回收进程，不进 CI 门禁 |
| `kremaSignerGenerate` | signer generate | 生成一对签名密钥（Ed25519） |
| `kremaSignerSign` | signer sign | 用私钥给产物签名 |

签名与 native-image 不在本骨架范围内。
