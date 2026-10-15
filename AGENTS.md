# AGENTS.md

## 行为规范

- 检查skill authoring-gradle-builds是否存在，不存在则执行install_gradle_skills

## 文档规范

- `AGENTS.md` 只写约束。写「必须 / 禁止 / 优先」。
- `README.md` 只写用法与介绍。写「这是什么、怎么跑」。
- 所有约束都缩略为一个列表项，放在章节下。

## 注释规范

- 用户是在构建脚本里直接命名、配置或调用的类型，方法，属性视为公开api。
- 不用注释复述代码。注释解释「为什么」，不解释「是什么」。
- 针对公开api，使用kotlin doc契约描述，注释不应复述读者一眼就能从代码看出的信息；它应补充代码无法表达的信息，包括意和必要的背景。但是不使用块标签。应对每个属性进行注释。
- 针对内部api，应该尽量精简，不备注或者精简的kotlin doc。一行内容的doc就让注释整体保持一行。一行注释最多50个字。。
- 错误抛出总是标注`@Throws(ExactException.class)`

## 代码规范

- 代码合并/提交到远程时对改动的模块执行 `./krema-gradle-plugin/gradlew spotlessApply`（或 `./krema-demo/gradlew`）。而平时修改不应该频繁执行这个行为。
- 测试方法名必须 ≤30 字符；每个 `@Test` 方法必须加 `@DisplayName` 标注中文名。
- 使用链式调用风格。
- 优先把 task 的 `dependsOn` 写在配置开头（`description` 或 `configureProject` 之后）。

## 提交规范

- pr最终合并的commit遵循`[chore,ref,feat,fix]:` 开头, 如果其关联issue，应该在末尾引用issue结尾。例: `feat: 一个功能 (#1)`

## Agent skills

### Issue tracker

- Issue 与 spec 都放在本仓库的 CNB Issues，一律用 `cnb` CLI 操作（不是 `gh`）。见 `docs/agents/issue-tracker.md`。

### Domain docs

- single-context：根级 `GLOSSARY.md`，不用 ADR。见 `docs/agents/domain.md`。
