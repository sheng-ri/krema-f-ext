# Krema Gradle 集成

把 Krema 桌面框架的构建编排收进 Gradle 插件后形成的领域：出一个能在本机启动、也能分发给用户的应用产物。

## Language

**krema 块**:
Gradle 构建脚本里唯一的 Krema 配置入口。所有构建输入（含配置文件的位置）都在 `krema { }` 里给出；不写 `krema { }` 意味着采用它自己的默认值。
_Avoid_: krema.toml（不再是配置源，只在 `krema` 块显式指定路径时作为默认值来源）、命令行开关

**构建档位（dev / prod）**:
产物形态的两档。它不是"调了什么命令"，而是"产出什么形态的东西"。共两档，不扩展。
_Avoid_: profile、variant、环境、flavor

**dev 档位**:
拿到本机开发用产物的那一档。与 prod 的差别由插件规定：dev 不把前端产物收进 classpath，应用转而从 devUrl 拉前端；
prod 把前端产物收进 classpath，可脱离开发环境分发。两档之间差什么不是交给使用方配置的开放项。
_Avoid_: 开发模式、debug、profile

**prod 档位**:
拿到可分发的那一档产物。这是默认档。
_Avoid_: release、生产环境

**bundle**:
一个自带最小运行时的可分发产物。拆成两个正交维度：**分发形态**（便携包 / 平台包）与**构建档位**（dev / prod），
四张 task 是这两个维度的组合。bundle 是构建的产物，不是「发行时才走」的一个步骤。
_Avoid_: 包、安装包、package

**分发形态**:
bundle 的形态维度，只有两种：**便携包**（dist，跨平台统一的 zip）与**平台包**（platform，本平台原生的可安装件）。
每种形态各有两张 dev / prod task。
_Avoid_: 档位、variant

**便携包**（dist）:
不分平台的分发形态：jpackage 产出的 app-image 目录，外加一份 `<app>-<version>-<target>.zip`。解压即用，不需安装。
_Avoid_: 免安装版、portable（与「便携包」同义，但中英混排时统一用中文术语）

**平台包**（platform）:
本平台原生的可安装件，由 `BundleFormat` 枚举配置要出哪些：macOS 出 `.app`/`.dmg`/`.pkg`，Windows 出 `.exe`/`.msi`，
Linux 出 `.AppImage`/`.deb`/`.rpm`。列表可空——空即本平台不出平台包。
_Avoid_: 安装包（泛称，不区分形态）、native 包

**运行时镜像**（runtime image）:
一份最小化的 Java 运行时，由 jlink 从应用实际用到的模块裁出，随 bundle 分发。使用方机器无需预装 JDK。
_Avoid_: JRE、jlink 产物

**应用身份**（bundle identity）:
让原生特性认出「这是一个应用」的形态：macOS 上是 `.app` 目录。此前 GLOSSARY 把「bundle」定义成这个东西，
现已与「bundle」区分开——它是 bundle 的必要属性之一，不是 bundle 本身。
_Avoid_: bundle（指称身份时）

**资产路径**（assets_path）:
前端产物在 Java classpath 中的落点，默认 `web`。前端 `dist` 进的是 app jar 里的这个路径，不是 bundle 目录下的某个文件夹。
_Avoid_: 前端目录、dist 目录（指 classpath 上的落点时）

**应用进程**:
由 Krema 主类启动的那个 JVM 进程，WebView 与原生窗口都在它里面。
_Avoid_: 后端

**dev server 进程**:
前端热更新服务的进程。与应用进程是两个独立进程，但生命周期同起同落。
_Avoid_: 前端（指进程时）
