# AGENTS.md

## 文档

- `AGENTS.md` 只写约束。写「必须 / 禁止 / 优先」。
- `README.md` 只写用法与介绍。写「这是什么、怎么跑」。
- 所有约束都缩略为一个列表项，放在章节下。

## 代码规范

- 注释只写在 public API 上：对外库暴露的 API、模块之间的交互 API。内部实现省略注释。
- 不用注释复述代码。注释解释「为什么」，不解释「是什么」。
- 提交前 `./gradlew spotlessCheck` 必须干净。

## 上游

- `BirthCat-Dev/krema-f` 只读，零改动，无例外。
- 上游能力只从 Central 坐标 `build.krema:krema-core` / `krema-processor` 取。
- 不引 `krema-cli`，不镜像 `krema-cli`：它是 picocli 终端外壳，编排逻辑与 `System.out` 长在一起，且上游刻意不发它到 Maven。要复用的包体与签名逻辑在 `krema-cli-spi` 自研。
- `krema-cli` 源码一个字节都不进本仓库。

## 提交规范

- pr最终合并的commit遵循`[chore,ref,feat,fix]:` 开头, 末尾引用issue结尾。例: `feat: 一个功能 (#1)`
