# AGENTS.md

## 文档

- `AGENTS.md` 只写约束。写「必须 / 禁止 / 优先」。
- `README.md` 只写用法与介绍。写「这是什么、怎么跑」。
- 所有约束都缩略为一个列表项，放在章节下。

## 代码规范

- 注释只写在 public API 上：对外库暴露的 API、模块之间的交互 API。内部实现省略注释。
- 不用注释复述代码。注释解释「为什么」，不解释「是什么」。
- 提交前 `./gradlew spotlessCheck` 必须干净。

## 外部 skill

- 涉及 Gradle API（`org.gradle.api` / `org.gradle.api.tasks` / `org.gradle.kotlin.dsl`）的改动，动手前必须先装对应版本的 skill。

```bash
npx skills add \
  "https://npm.cnb.cool/BirthCat-Dev/skills/-/packages/@birthcat-dev/gradle-plugin-api-9.8.0/-/@birthcat-dev/gradle-plugin-api-9.8.0-9.8.0.tgz" \
  "https://npm.cnb.cool/BirthCat-Dev/skills/-/packages/@birthcat-dev/gradle-plugin-tasks-9.8.0/-/@birthcat-dev/gradle-plugin-tasks-9.8.0-9.8.0.tgz" \
  "https://npm.cnb.cool/BirthCat-Dev/skills/-/packages/@birthcat-dev/gradle-kts-9.8.0/-/@birthcat-dev/gradle-kts-9.8.0-9.8.0.tgz" \
  -g -a codebuddy -y
```

## 提交规范

- pr最终合并的commit遵循`[chore,ref,feat,fix]:` 开头, 末尾引用issue结尾。例: `feat: 一个功能 (#1)`

## Agent skills

### Issue tracker

- Issue 与 spec 都放在本仓库的 CNB Issues，一律用 `cnb` CLI 操作（不是 `gh`）。见 `docs/agents/issue-tracker.md`。

### Domain docs

- single-context：根级 `GLOSSARY.md`，不用 ADR。见 `docs/agents/domain.md`。
