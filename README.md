# krema-f-ext

把 [Krema](https://cnb.cool/BirthCat-Dev/krema-f) 的构建编排能力收进 Gradle 插件。

Krema 是一套 Java + 系统 WebView 的桌面框架，上游自带一个 `krema` CLI 来初始化项目、 跑开发服务器、构建与打包。本项目在它之上做二次开发：让
Gradle 拥有同一套构建编排能力。

## 模块

| 模块                  | 职责                                                            |
|-----------------------|-----------------------------------------------------------------|
| `krema-gradle-plugin` | Gradle 插件本体：声明 task、绑定输入输出、实现 Krema 的构建编排 |
| `krema-demo`          | 消费插件的示例工程，也是 e2e 的载体                             |

两个模块各自独立成 Gradle 根，各自带 wrapper，没有根构建。

## 构建

需要 JDK 25。Wrapper 已锁 Gradle 9.8，直接用即可。

```bash
./krema-gradle-plugin/gradlew build          # 编译 + 测试 + 格式检查
./krema-gradle-plugin/gradlew spotlessCheck  # 只跑格式检查
./krema-gradle-plugin/gradlew spotlessApply  # 自动修格式

./krema-demo/gradlew build                   # demo 同样一套
```

## 插件

在 `build.gradle.kts` 里应用插件，然后通过 `krema { }` 扩展配置。`krema { }` 是唯一的配置入口， 所有构建输入都写在这里；插件自己引入
JVM 基础能力，你不必在脚本里先写别的插件。

### krema { }

`krema { }` 的值逐项压过 `krema.toml`；没写的项一律以 toml 为准。

```kotlin
import top.birthcat.kremaf.ext.gradle.api.BundleFormat

krema {
    // krema.toml 位置，默认项目根下的 krema.toml；显式指定却不存在会硬失败
    configFile = layout.projectDirectory.file("krema.toml")

    // 身份与开发地址，压过 toml 的 [build]
    mainClass = "com.example.Main"
    devUrl = "http://localhost:5173"
    frontendOutDir = layout.projectDirectory.dir("dist")

    frontend {
        // 前端工程目录，相对项目根；不写就是项目根
        dir = "front"
        buildCommand = "npm run build"
        installCommand = "pnpm install"
    }

    runtime {
        // 非空即取代 jdeps 推导
        modules = setOf("java.base", "java.desktop", "java.logging", "java.net.http", "jdk.httpserver")
    }

    bundling {
        // 只打宿主平台那份；写错平台的格式会被静默丢弃
        macosFormats = setOf(BundleFormat.APP, BundleFormat.DMG, BundleFormat.PKG)
        windowsFormats = setOf(BundleFormat.EXE, BundleFormat.MSI)
        linuxFormats = setOf(BundleFormat.APP_IMAGE, BundleFormat.DEB)

        // 没配图标就跳过 --icon；三处都空时回落到 toml 的 [bundle].icon
        macIcon = layout.projectDirectory.file("icons/icon.icns")
        winIcon = layout.projectDirectory.file("icons/icon.ico")
        linuxIcon = layout.projectDirectory.file("icons/icon.png")

        // appimagetool 不在 PATH 时是否自动下载
        appImageToolAutoDownload = true

        // 仅 Windows 格式生效，逐项压过 toml 的 [bundle.windows]
        windows {
            dirChooser = true
            helpUrl = "https://example.com/help"
            menu = true
            menuGroup = "Demo"
            perUserInstall = true
            shortcut = true
            shortcutPrompt = true
            updateUrl = "https://example.com/update"
            upgradeUuid = "6f1d2c34-0000-4000-8000-000000000000"
        }
    }
}
```

### krema.toml

`krema.toml` 存应用身份与打包参数。下面的示例混排了上游 Krema 的配置项和本插件实际读的键：
`![自定义]` 标的是本插件自己的键名，`![未实现]` 是暂时不读、留待后续的段，
`![不计划支持]` 是确定不会做的段。

```toml
[package]
name = "my-app"
version = "1.0.0"
identifier = "com.example.myapp"
description = "My Krema Application"

# ![未实现]: [window] 段当前不读；标题、尺寸、resizable 与 decorations 都由应用代码设置
[window]
title = "My App"
width = 1200
height = 800
min_width = 800
min_height = 600
resizable = true
fullscreen = false
decorations = true

[build]
frontend_command = "npm run build"
# ![不计划支持]: 插件不托管前端 dev server，也不为它生成 task
frontend_dev_command = "npm run dev"
# 由 kremaDevRun 注入为 KREMA_DEV_URL；别名 dev_url 也接受
frontend_dev_url = "http://localhost:5173"
# 前端产物目录；别名 frontend_out_dir 也接受，krema.frontendOutDir 压过两者
out_dir = "dist"
# 额外 Java 源码根，与默认的 src/main/java 并存
java_source_dir = "src-java"
# ![不计划支持]: 前端产物目录由 out_dir 决定，assets 只是 jar 内固定落点
assets_path = "assets"
main_class = "com.example.Main"

[bundle]
icon = "icons/icon.icns"
identifier = "com.example.myapp"
copyright = "Copyright 2024 Example Inc."

# ![未实现]: macOS 签名、公证与 entitlements 尚未接入
[bundle.macos]
signing_identity = "Developer ID Application: Example Inc."
entitlements = "entitlements.plist"
notarization_apple_id = "dev@example.com"
notarization_team_id = "ABCDEF1234"

[bundle.windows]
# ![自定义]: 安装器开关，对应 jpackage 的 --win-*；dir_chooser / shortcut / shortcut_prompt 缺省开
dir_chooser = true
help_url = "https://example.com/help"
menu = true
menu_group = "Demo"
per_user_install = true
shortcut = true
shortcut_prompt = true
update_url = "https://example.com/update"
upgrade_uuid = "6f1d2c34-0000-4000-8000-000000000000"
# ![未实现]: 安装器签名与时间戳服务尚未接入
signing_certificate = "cert.pfx"
timestamp_url = "http://timestamp.digicert.com"

# ![未实现]: 权限声明尚未进入构建流程
[permissions]
allow = [
    "fs:read",
    "fs:write",
    "clipboard:read",
    "clipboard:write",
    "notification",
    "dialog",
    "shell:open",
]

# ![未实现]: 自动更新配置当前不读，更新逻辑由应用自己接
[updater]
pubkey = "dW50cnVzdGVkIGNvbW1lbnQ6IG1pbmlzaWduIHB1YmxpYyBrZXkK..."
endpoints = [
    "https://releases.example.com/latest.json",
]
check_on_startup = true
timeout = 30

# ![未实现]: 深链注册当前不读
[deep-link]
schemes = ["myapp", "myapp-dev"]

# ![不计划支持]: 启动图由应用自己渲染
[splash]
enabled = true
image = "splash.png"
width = 480
height = 320
show_progress = true

# ![未实现]: env 覆盖层当前不读，下面三段都不会生效
[env.development]
[env.development.build]
frontend_dev_url = "http://localhost:3000"

[env.development.window]
title = "My App (Dev)"

[env.staging]
[env.staging.updater]
endpoints = ["https://staging.example.com/updates.json"]

[env.production]
[env.production.updater]
endpoints = ["https://releases.example.com/updates.json"]
```

### task

以下 task 归属 `krema` 分组：

| task                  | 对应上游 CLI    | 做什么                                                                                                                                                                                                                 |
|-----------------------|-----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `kremaInit`           | init            | 生成项目骨架（`krema.toml` + 主类 + `.gitignore`），主类落在 `src/main/java`；主类读 `KREMA_DEV_URL` 并用 `prodAssets("/assets")`                                                                                      |
| `kremaBuild`          | build           | 聚合后端与前端：让 Gradle 编译主 source set，跑前端生产构建（`frontend.buildCommand`），把后端 app jar 与独立的前端 assets jar 作为可缓存、UP-TO-DATE 的产出收进 `build/krema/deps`                                    |
| `kremaFrontendBuild`  | —               | 跑前端生产构建（`frontend.buildCommand`，可选 `installCommand` 先装依赖），产物目录取 `krema.toml` 的 `out_dir`（或 `frontend_out_dir`）声明成可缓存、UP-TO-DATE 的输出；被 `kremaBuild` 依赖，也可单独跑只重做前端  |
| `kremaRuntimeImage`   | —               | 用 jdeps 推导模块、jlink 裁出最小运行时镜像，产物落 `build/krema/runtime`；`runtime.modules` 非空时改用你给的清单                                                                                                      |
| `jdepsModules`        | —               | 打印 jdeps 推导出的运行时模块清单，不裁镜像。想手动指定模块前先跑它看清推导结果                                                                                                                                        |
| `jdepsModulesVerbose` | —               | 打印 `jdeps -v` 的逐包依赖明细，并把原始输出写到 `build/krema/jdeps-verbose.txt` 供事后分析                                                                                                                            |
| `kremaBundle`         | bundle          | 未压缩、可直接运行的 app-image 目录，自带 jlink 裁出的最小运行时，产物落 `build/krema/app`；是所有平台包的共同底座                                                                                                     |
| `kremaPlatform<格式>` | bundle          | 单个平台包格式（如 `kremaPlatformDeb` / `kremaPlatformAppImage`），**按需注册**：只注册宿主平台 `bundling` 里配置了的那些，复用 `kremaBundle` 的 app-image 打成本格式可安装件，产物落 `build/krema/platform/<格式 id>` |
| `kremaBundlePlatform` | bundle          | 聚合：依赖宿主平台配置了的各 `kremaPlatform<格式>` task；没配格式时空跑成功                                                                                                                                            |
| `kremaDevRun`         | —               | 开发期：在 classpath 上起后端应用进程并把前端地址作为 `KREMA_DEV_URL` 注入；不依赖打包，前端 dev server 由你自己开                                                                                                     |
| `kremaBundleRun`      | —               | 起 `kremaBundle` 刚打出的那份 app-image，长驻前台，中断时回收进程，不进 CI 门禁                                                                                                                                        |
| `kremaSignerGenerate` | signer generate | 生成一对签名密钥（Ed25519）                                                                                                                                                                                            |
| `kremaSignerSign`     | signer sign     | 用私钥给产物签名                                                                                                                                                                                                       |

`krema { bundling { } }` 只按宿主平台选格式：写错平台的格式会被静默丢弃而非报错；图标三属性没配就跳过，`krema.toml` 的
`[bundle].icon` 按宿主平台兜底。`[bundle.windows]` 只对 exe/msi 生效：`dir_chooser`、`shortcut`、`shortcut_prompt` 缺省
`true`，其余布尔键写 `true` 才传，字符串不写即不传；`krema { bundling { windows { } } }` 里的同名属性逐项压过 toml，写
`false` 也算压过。

签名与 native-image 不在本骨架范围内。
