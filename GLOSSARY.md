# Krema Gradle 集成

把 Krema 桌面框架的构建编排收进 Gradle 插件后形成的领域：出一个能在本机启动、也能分发给用户的应用产物。

## Language

**krema 块**:
Gradle 构建脚本里唯一的 Krema 配置入口。所有构建输入（含配置文件的位置）都在 `krema { }` 里给出；不写 `krema { }` 意味着采用它自己的默认值。
_Avoid_: krema.toml（不再是配置源，只在 `krema` 块显式指定路径时作为默认值来源）、命令行开关

**bundle**:
`kremaBundle` 的产出：一个自带最小运行时、**未压缩、可直接运行**的 app-image 目录。它是后续所有平台包的共同底座，
本身不是分发给用户装的东西。
_Avoid_: 包、安装包、package、便携包（zip 形态已不存在）

**平台包**:
本平台原生的可安装件，由 `BundleFormat` 枚举配置要出哪些：macOS 出 `.app`/`.dmg`/`.pkg`，Windows 出 `.exe`/`.msi`，
Linux 出 `.AppImage`/`.deb`/`.rpm`。配了几个格式就注册几张 `kremaPlatform<格式>` task，列表为空即不出平台包。
_Avoid_: 安装包（泛称，不区分形态）、native 包、分发形态（该维度已删除）

**运行时镜像**（runtime image）:
一份最小化的 Java 运行时，由 jlink 从应用实际用到的模块裁出，随 bundle 分发。使用方机器无需预装 JDK。
_Avoid_: JRE、jlink 产物

**运行时模块清单**（runtime modules）:
决定裁进运行时镜像的 JDK 模块名集合。两个来源：默认由 jdeps 按应用 classpath 推导；
`krema { runtime { modules } }` 非空时由使用方指定，**取代**推导。
_Avoid_: jdeps 推导结果、模块列表（两者都指同一个东西，且「模块列表」没说清是给谁用的）

**应用身份**（bundle identity）:
让原生特性认出「这是一个应用」的形态：macOS 上是 `.app` 目录。此前 GLOSSARY 把「bundle」定义成这个东西，
现已与「bundle」区分开——它是 bundle 的必要属性之一，不是 bundle 本身。
_Avoid_: bundle（指称身份时）

**前端资产**:
前端产物在独立 assets jar 里的落点目录，字面固定为 `assets`。Krema 主类用 `prodAssets("/assets")` 按它读，
两边必须字面一致，所以不开放配置。前端是「整个应用」的资源，由 `kremaBuild` 打成单独的 assets jar 落到 classpath 上，
而不是合并进后端 app jar：Krema 从整个 classpath 扫 `assets/*`，独立 jar 同样能被读到，后端 app jar 得以保持纯粹。
_Avoid_: 资产路径（这个可配项已不存在）、前端目录（那是磁盘上的目录，不是 jar 里的落点）

**前端目录**:
前端工程所在的目录：`package.json`、`node_modules`、前端源码与 `dist` 都在它下面，前端命令也在它里面执行。
由 `frontend_dir` 配，不配就是项目根—— 于是既有项目不写这个键也照旧。
_Avoid_: 前端资产（jar 里的落点）、dist 目录（只是前端目录下的一个子目录）

**应用进程**:
由 Krema 主类启动的那个 JVM 进程，WebView 与原生窗口都在它里面。
_Avoid_: 后端

**dev server 进程**:
前端热更新服务的进程。与应用进程是两个独立进程，**由使用方自己起停**——插件不托管它，也不为它提供 task。
`kremaDevRun` 只把它的地址作为环境变量交给应用进程，用不用由应用代码决定。
_Avoid_: 前端（指进程时）
