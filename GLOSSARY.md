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
拿到本机开发用产物的那一档。有些平台的原生特性要求应用具备正经 bundle 身份（macOS 必须是 `.app`），这类平台在 dev 档就要出 bundle；其余平台上是调试信息一类更轻的差异。两档之间差什么由插件规定，不是交给使用方配置的开放项。
_Avoid_: 开发模式、debug、profile

**prod 档位**:
拿到可分发的那一档产物。这是默认档。
_Avoid_: release、生产环境

**bundle**:
一个让原生特性认出"应用身份"的应用形态；macOS 上是 `.app` 目录。dev 档在某些平台上也必须有它，所以它不是「发行时才走」的一个步骤。
_Avoid_: 包、安装包、package

**应用进程**:
由 Krema 主类启动的那个 JVM 进程，WebView 与原生窗口都在它里面。
_Avoid_: 后端

**dev server 进程**:
前端热更新服务的进程。与应用进程是两个独立进程，但生命周期同起同落。
_Avoid_: 前端（指进程时）
