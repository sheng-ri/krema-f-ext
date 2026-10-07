# AGENTS.md

本文件只写**约束**。不写事实、不写实现、不写用法。

约束与文档的分工：

- `AGENTS.md`：只写约束。写「必须 / 禁止 / 优先」。
- `README.md`：只写用法与介绍。写「这是什么、怎么跑」。
- 追踪一律走 CNB Issue。**不建 ADR**，决策留痕放 Issue。

## 代码

- 注释只写在 public API 上：对外库暴露的 API、模块之间的交互 API。内部实现省略注释。
- 不用注释复述代码。注释解释「为什么」，不解释「是什么」。
- 提交前 `./gradlew spotlessCheck` 必须干净。

## 分层

- `krema-cli-spi` 是中间层：纯 Java，**不得**依赖 Gradle API，**不得**依赖 Maven API。
- `krema-gradle-plugin` 是薄壳：只放 Gradle 差异实现与 task 声明，不放编排逻辑。
- 能委托上游就委托，否则自己写。委托只走 Central 公开坐标。

## 上游

- `BirthCat-Dev/krema-f` **只读**，零改动，无例外。
- 上游能力通过 Central 坐标 `build.krema:krema-core` / `krema-processor` 消费。
- `krema-cli` 源码一个字节都不进本仓库。
- 本仓库 Apache-2.0。`NOTICE` 保留「bundling 接口形状参考 Krema CLI」的诚实痕迹。

## Gradle

- 目标版本 Gradle 9.8，Kotlin DSL。
- task action 里**不得**注入 `Project`（已弃用）。
- 输入走 `@Input` / `@InputFile`；输出走 `@OutputDirectory DirectoryProperty`；进程调用走 `@Inject ExecOperations`。
- 不得现场取 `project.layout`；路径一律由 Property 提供。
- `krema.toml` 所在目录是解析基准。不得依赖 `user.dir`；`krema.toml` 与 `.env` 声明成 `@InputFile`。
- Configuration Cache 必须干净：第二次运行命中且无 deprecation 警告。

## 测试

- 断言分层：**文件 I/O 优先**（`FileAssert`），终端输出兜底（`StdoutAssert`）。
- 终端断言只用于无产物场景（如 `--dry-run` 的命令拼装）。
- 编排逻辑在 `krema-cli-spi` 里纯 JUnit 测，不起 Gradle。TestKit 只验薄壳。
- 端到端（真开窗口跑 webview）**不进 PR 门禁**，放 `web_trigger`。

## 云构建

- PR 门禁跑：`./gradlew build spotlessCheck`。这就是全部。
- 不进门禁：端到端窗口测试、真打 `.deb`/`.rpm`/`.AppImage`、macOS 真启动。
- 构建镜像在 `.cnb/build/Dockerfile`，系统包清单在 `.cnb/build/apt-packages.txt`。
- 凭据只走仓库环境变量，不得硬编码进 `.cnb.yml` 或任何提交文件。
- 失败先看 stage 名定位，再看 `./gradlew` 原始输出，别只看摘要。
