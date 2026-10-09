# Handoff — #55 task 家族与 dev/prod 两档 + run 阻塞回收（WIP）

> 状态：实现完成，但有 4 个 TestKit 行为断言在云环境里失败，**构建本身编译通过**，`spotlessCheck` 干净，仅 `test` 红。
> 分支：`ticket/55-task-family`（从 `spec/gradle-native-plugin` @ `d0ddabb` 拉出）。
> 中断原因：用户下线，要求先把半成品与 handoff 推到非主分支。

## 这一票做了什么

把 spec #51 的「固定 task 家族 + dev/prod 两档 + run 阻塞回收」落到代码：

- **task 家族重排成 8 张**（`KremaTaskNames.kt`）：删掉 `kremaBuildNative` / `kremaBundle` / `kremaDev`（对应文件已删），新增
  `kremaBundleDev` / `kremaBundleProd` / `kremaBundleRunDev` / `kremaBundleRunProd`。
- **基类拆分**（`KremaConfigTask.kt` 新增）：把配置输入（projectDir / configFilePath / configFile* / mainClass / `config()` / `bundleSettings(outputDir)` / `configureProject` / `kremaApplicationClasspath`）从 `KremaProjectTask` 提到 `KremaConfigTask`；`KremaProjectTask` 只补 `@OutputDirectory outputDir` + 无参 `bundleSettings()`。run task 直接继承 `KremaConfigTask`，避免声明产物目录与 bundle task 抢同一个输出根。
- **打包 task 重写**（`KremaBundleTask.kt`）：一张类靠 `dev` 分档。dev = 最小形态（`PlatformBundler.createDevBundle`）+ 不打包 dist；prod = 完整形态（`createProductionBundle`）+ 把前端 dist 收进 bundle。classpath 直接取 Gradle `runtimeClasspath`（`@Classpath` 输入）。
- **run task 新增**（`KremaBundleRunTask.kt` + `KremaProcessCleanupService.kt`）：dev 档先起前端 dev server 进程，再起 bundle 应用进程，两个进程都登记到一个 `BuildService`，构建收尾（含 Ctrl-C 取消）时统一杀整棵进程树；阻塞在前台（`app.waitFor()`）。
- **部署落地**（`PlatformBundler` 加 `bundlePath` / `javaTargetDir` / `distTargetDir`，`FileOps.copyTree` 新增）：把 classpath 与 dist 摊进 bundle 内对应目录（macOS `.app/Contents/Resources/Java`、`../../../.krema/dev/Java`；Windows `java\`；Linux `usr/share/<app>/java`）。
- **依赖接线**（`KremaTasks.kt`）：`kremaBundleDev/Prod` 都 `dependsOn(kremaBuild)`，输出目录各占 `build/kerma/dev`、`build/kerma/prod`；`kremaBundleRun*` `dependsOn` 对应 bundle task 并 `usesService(cleanup)`。
- 测试改名为「八张 task」一组；`bundleRunsRealOrchestrationPath` 拆成 `bundleProdWritesToProdOutputDir` + `bundleDevAndProdUseSeparateOutputDirDirs`；`explicitConfigFileMissingFails` 改用假 npm 套件（因为现在 prod bundle 也走前端构建）。
- README 的 task 表同步成 8 张。

## 待接手的问题（关键 blocker）

**4 个测试失败**：`defaultConfigFileAbsentDoesNotFail`、`frontendDistIsUpToDateOnSecondRun`、`frontendBuildFailureSurfacesCommandOutput` 之外，具体报错的是另外 4 个——实际是 `signerGenerateProducesEd25519KeyPair`、`bundleProdWritesToProdOutputDir`、`defaultConfigFileAbsentDoesNotFail`（dist 断言）、`bundleDevAndProdUseSeparateOutputDirs`。共同特征：**构建本身 BUILD SUCCESSFUL，但测试 JVM 读不到刚产出的文件**（断言 `isFile=false`）。

排查线索（看 `KremaFrontendBuildTask` 的临时调试输出 `/tmp/krema-debug.txt` 时发现的怪象）：在 plugin 进程里 `walkTopDown` 能列到 `build/kerma/dist/index.html`，但同一路径 `java.io.File.exists()` 却返回 false——**疑似 TestKit 拉起的 Gradle daemon 写出的产物，在本测试 JVM 这边看不到**（同一 `/tmp/junit-xxx` 目录、同机）。高度怀疑是云环境里 daemon 进程的写可见性 / 命名空间问题，或 `@CacheableTask` 在 TestKit 跨临时目录共享本地 build cache 导致 cache-hit 时没有把产物落到当前项目目录。

**建议下一步**：
1. 先确认是不是 `@CacheableTask(kremaFrontendBuild)` + TestKit 共享缓存：试在 `runGradle` / `runGradleWithFakeNpm` 里加 `--rerun-tasks` 或 `--no-build-cache` 重跑，看 4 个测试是否变绿。
2. 若仍红，确认 daemon 写出的文件对测试进程是否可见（daemon 与 test 是否同一 mount namespace）——这层环境影响的不只 #55，#56 的 TestKit 断言也会撞。
3. 修复后照 #52–#54 的节奏 `git merge --no-ff ticket/55-task-family` 进 `spec/gradle-native-plugin`，然后开 #56（TestKit 四条行为断言：二次打 bundle UP-TO-DATE、外部命令失败透出输出、run 中断无孤儿进程、整体跑在 `--configuration-cache` 下）。

## 注意

- 提交前 `./gradlew spotlessCheck` 干净即可合（AGENTS.md 要求）；本分支目前 test 红，合入 integration 前务必先把上面 4 个测试救活。
- 不要在主分支上动；`ticket/55-task-family` 是非主分支，已推远端。
