# Handoff — #55 task 家族与 dev/prod 两档 + run 阻塞回收（WIP）

> 状态：实现完成。**本会话**补了测试改名 + `@DisplayName`、AGENTS.md 测试命名约束，并推翻了上一版「云沙箱不同 namespace」的根因假设，定位到一个与 `--configuration-cache` 开关相关的真问题（非纯环境可见性）。
> 分支：`ticket/55-task-family`（从 `spec/gradle-native-plugin` @ `d0ddabb` 拉出）。
> 中断原因：用户下线，先把进展与 handoff 留非主分支。

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

## 待接手的问题（关键 blocker，根因已修正）

**4 个测试失败**——实际是 `signerGenerateKeyPair`、`bundleProdOutputDir`、`defaultConfigAbsentOk`（dist 断言）、`bundleDevProdSeparateOutputDirs`。共同现象：构建 BUILD SUCCESSFUL，但测试 JVM 读不到刚产出的文件（断言 `isFile=false`）。

### 上一版假设被推翻

旧 handoff 猜「TestKit daemon 与 test JVM 不在同一 mount namespace，云沙箱文件系统隔离」。**这条是错的**，有探针证据：

- 在 `kremaSignerGenerate` 的 `doLast` 里 `readlink /proc/self/ns/mnt`，与测试 JVM 的 `/proc/self/ns/mnt` 对比，两者完全相同：
  `PROBE_TEST_NS=mnt:[4026545280]` == `PROBE_BUILD_NS=mnt:[4026545280]`。
- 因此 build 与 test **同属一个 mount namespace**，可见性问题另有原因。

### 修正后的真实线索（重点）

探针（临时、已回退）跑**不带 `--configuration-cache`** 的 `kremaSignerGenerate` 时，观察到：

- task `outcome=SUCCESS`、`> Task :kremaSignerGenerate` 确实执行了（非 UP-TO-DATE）；
- 但该 task **自己的 `doLast`**（与 task action 同进程）对 `build/kerma/keys/krema.key` 做 `stat -c %d:%i` 拿到**空**（文件不在）；
- 测试 JVM 侧同样 `NOT_VISIBLE`；
- 而**更早一次带 `--configuration-cache` 的 DIAG 运行**，`walkTopDown` 能列到 `build/kerma/keys/krema.key`。

即：**开了 `--configuration-cache` 时文件落在 `build/kerma/keys/krema.key`；不开时连 task 自己的 doLast 都找不到它**。这指向一个与 config-cache 开关相关的**真实行为差异**（不是纯跨进程可见性），需要继续追：

1. **确认 key 到底落在哪**：重做探针，让 `doLast` 里 `walkTopDown` 打印整棵项目树（`BUILD_TREE`），看 no-CC 下文件实际路径。（上次探针因测试侧编译错误没跑出这个打印，已回退测试文件，需重建。）
2. **追 `outputDir` 解析在 CC 开/关下的差异**：`KremaProjectTask.configureProject` 里 `outputDir.convention(ext.outputDir.orElse(layout.buildDirectory.dir("krema")))`。重点怀疑 CC 让 task 在另一绝对路径写产物，或 `layout.buildDirectory` 在 CC 下解析不同；也可能 `KremaSignerGenerateTask` / `Ed25519Signer` 的写入路径与 `@OutputFile privateKey` 约定不一致。
3. 定位后决定修法：若是「断言路径 vs 产物路径不匹配」→ 修测试；若是「`outputDir` 在 CC 下解析错位」→ 修插件（这会是 #55 的实现 bug，而非环境）。

### 建议下一步

1. 重建探针抓 `BUILD_TREE`，确认 no-CC 下 key 的真实落点。
2. 对比 `runGradle`（带 `--configuration-cache`）与 no-CC 两种跑法下 `outputDir` 的绝对解析值（在 `doLast` 里打印 `layout.buildDirectory.asFile.absolutePath` 和 `outputDir.get().asFile.absolutePath`）。
3. 根因若属插件 bug，修 `KremaSignerGenerateTask` / `KremaBundleTask` 的输出路径；若属测试断言路径写错，修 `KremaPluginTest` 的断言。
4. 同样现象会波及 `bundleProdOutputDir` / `bundleDevProdSeparateOutputDirs` / `defaultConfigAbsentOk`（都是「读 build/kerma 下产物」的断言），根因应同源，修一处即全绿。
5. 修复后 `git merge --no-ff ticket/55-task-family` 进 `spec/gradle-native-plugin`，再开 #56（spec 的四条 TestKit 断言）。

## 注意

- 提交前 `./gradlew spotlessCheck` 干净即可合（AGENTS.md 要求）。当前 `ticket/55-task-family` 上 `dff0429` / `cd10a07` 已满足。
- 不要在主分支上动；`ticket/55-task-family` 是非主分支。
- 4 个测试仍红是**未解决的真问题**（疑似 config-cache 相关的产物落盘路径差异），不要当成「云环境必红」而带病合并——先按上面步骤定位。
