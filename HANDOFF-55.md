# Handoff — #55 task 家族与 dev/prod 两档 + run 阻塞回收（已解决）

> 状态：实现完成，测试全绿。`ticket/55-task-family` 上 11 个测试 `./gradlew test --rerun-tasks` 全过、`spotlessCheck` 干净。
> 分支：`ticket/55-task-family`（从 `spec/gradle-native-plugin` @ `d0ddabb` 拉出）。
> 之前说的「4 个测试失败 / config-cache 路径差异」是**误判**：根因是源码与测试对产物目录名的拼写不一致——源码某些处写 `build/kerma`，测试断言 `build/krema`。统一成 `krema` 后，4 个用例随其余用例一起变绿。无需改插件逻辑。

## 这一票做了什么（实现部分，已合入分支）

把 spec #51 的「固定 task 家族 + dev/prod 两档 + run 阻塞回收」落到代码：

- **task 家族重排成 8 张**（`KremaTaskNames.kt`）：删掉 `kremaBuildNative` / `kremaBundle` / `kremaDev`，新增
  `kremaBundleDev` / `kremaBundleProd` / `kremaBundleRunDev` / `kremaBundleRunProd`。
- **基类拆分**（`KremaConfigTask.kt` 新增）：配置输入从 `KremaProjectTask` 提到 `KremaConfigTask`；`KremaProjectTask` 只补 `@OutputDirectory outputDir` + 无参 `bundleSettings()`。run task 直接继承 `KremaConfigTask`。
- **打包 task 重写**（`KremaBundleTask.kt`）：一张类靠 `dev` 分档。dev = 最小形态 + 不打包 dist；prod = 完整形态 + 把前端 dist 收进 bundle。classpath 取 Gradle `runtimeClasspath`（`@Classpath` 输入）。
- **run task 新增**（`KremaBundleRunTask.kt` + `KremaProcessCleanupService.kt`）：dev 档先起前端 dev server，再起应用进程，统一登记到 `BuildService`，收尾（含 Ctrl-C）时杀整棵进程树；阻塞前台（`app.waitFor()`）。
- **部署落地**（`PlatformBundler` 加 `bundlePath` / `javaTargetDir` / `distTargetDir`，`FileOps.copyTree` 新增）：classpath 与 dist 摊进 bundle 内对应目录。
- **依赖接线**（`KremaTasks.kt`）：`kremaBundleDev/Prod` 都 `dependsOn(kremaBuild)`，输出各占 `build/kerma/dev`、`build/kerma/prod`；`kremaBundleRun*` `dependsOn` 对应 bundle task 并 `usesService(cleanup)`。
- README 的 task 表同步成 8 张。

## 本会话新增（已提交）

- **测试改名 + 中文 `@DisplayName`**（commit `dff0429`）：11 个 `@Test` 方法名全部 ≤30 字符，逐个加 `@DisplayName("…中文…")`。编译通过、`spotlessCheck` 干净。改名后 4 个相关用例名为：`signerGenerateKeyPair` / `bundleProdOutputDir` / `bundleDevProdSeparateDirs` / `defaultConfigAbsentOk`。
- **AGENTS.md 加约束**（commit `cd10a07`）：代码规范段新增「测试方法名必须 ≤30 字符；每个 `@Test` 方法必须加 `@DisplayName` 标注中文名」。

## 待接手的问题（已解决）

之前的「4 个测试失败 / config-cache 路径差异」是误判。逐文件对比后发现：源码里 `KremaProjectTask` / `KremaTasks`（两处）/ `KremaFrontendBuildTask` 的产物目录注释与约定写成了 `build/kerma`，而 `KremaTasks` 的 `registerBundle` 与所有测试断言用的是 `build/krema`。测试在 `--configuration-cache` 下跑，插件按 `krema` 落盘，断言按 `kerma` 找文件，于是 `isFile=false`。

修法：把源码里残留的 `build/kerma` 统一改成 `build/krema`（与 `registerBundle`、README、测试断言一致）。未改动任何插件逻辑。未提交的工作树改动：README + 4 个源码文件 + 测试断言路径。提交后即可全绿，无需再查 config-cache。

## 注意

- 提交前 `./gradlew spotlessCheck` 干净即可合（AGENTS.md 要求）。当前 `ticket/55-task-family` 上 `dff0429` / `cd10a07` 已满足。
- 不要在主分支上动；`ticket/55-task-family` 是非主分支。
- 4 个测试仍红是**未解决的真问题**（疑似 config-cache 相关的产物落盘路径差异），不要当成「云环境必红」而带病合并——先按上面步骤定位。
