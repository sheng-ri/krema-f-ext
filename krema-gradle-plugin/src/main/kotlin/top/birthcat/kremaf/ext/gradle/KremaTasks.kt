/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import top.birthcat.kremaf.ext.gradle.BUNDLE_DIR_NAME
import top.birthcat.kremaf.ext.gradle.appImageDirName
import top.birthcat.kremaf.ext.gradle.kremaPlatformTaskName
import top.birthcat.kremaf.ext.gradle.platformOutputDirName
import top.birthcat.kremaf.ext.gradle.task.KremaBuildTask
import top.birthcat.kremaf.ext.gradle.task.KremaBundleRunTask
import top.birthcat.kremaf.ext.gradle.task.KremaBundleTask
import top.birthcat.kremaf.ext.gradle.task.KremaDevRunTask
import top.birthcat.kremaf.ext.gradle.task.KremaFrontendBuildTask
import top.birthcat.kremaf.ext.gradle.task.KremaInitTask
import top.birthcat.kremaf.ext.gradle.task.KremaJdepsModulesTask
import top.birthcat.kremaf.ext.gradle.task.KremaJdepsModulesVerboseTask
import top.birthcat.kremaf.ext.gradle.task.KremaPlatformTask
import top.birthcat.kremaf.ext.gradle.task.KremaRuntimeImageTask
import top.birthcat.kremaf.ext.gradle.task.KremaSignerGenerateTask
import top.birthcat.kremaf.ext.gradle.task.KremaSignerSignTask

internal fun Project.registerKremaTasks() {
    val ext = extensions.getByType(KremaExtension::class.java)

    val cleanup =
        gradle.sharedServices.registerIfAbsent("kremaProcessCleanup", KremaProcessCleanupService::class.java) {}

    tasks.register(KremaTaskNames.INIT, KremaInitTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "Generate the Krema project scaffold (krema.toml + main class)"
        task.projectDir.convention(layout.projectDirectory)
        task.outputDir.convention(layout.projectDirectory)
    }

    tasks.register(KremaTaskNames.FRONTEND_BUILD, KremaFrontendBuildTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        // 前端产物落在 krema.toml 的 out_dir（默认 dist/，相对项目根），也就是前端工具链自己的落点。
        // 插件只能按它去读：读 build/krema 下的目录等于读一个前端工具从不写的地方，prod 的 app jar 会空着 assets/。
        task.description = "Run the frontend production build (npm); output dir from krema.toml out_dir, declared as cacheable"
        task.configureProject(ext)
        // 前端命令：toml 的 frontend_* 是底，`krema.frontend { }` 里显式写的压过它。
        val tomlFrontend = providers.provider { kremaConfig(ext).frontend }
        task.buildCommand.convention(tomlFrontend.map { it.buildCommand })
        ext.frontend.buildCommand.orNull
            ?.let { task.buildCommand.convention(it) }
        task.installCommand.convention(tomlFrontend.map { it.installCommand })
        ext.frontend.installCommand.orNull
            ?.let { task.installCommand.convention(it) }
        // 前端目录决定命令在哪跑、产物与输入在哪解析，都在这一层定下来；dist 落在它下面。
        conventionFrontendDir(ext, task.frontendDir)
        val tomlOutDir = providers.provider { kremaConfig(ext).build.outDir }
        task.distDir.convention(task.frontendDir.flatMap { front -> tomlOutDir.map { front.dir(it) } })
        // 前端输入在前端目录下解析；哪些锁文件存在就收集哪些，缺失的候选自然不在集合里。
        task.frontendInputs.from(
            listOf("package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml")
                .map { task.frontendDir.dir(it) },
        )
    }

    // 后端编译产物 + 运行期依赖 classpath：kremaBuild 与运行时镜像/依赖分析共用同一份。
    val classpath = kremaApplicationClasspath()

    tasks.register(KremaTaskNames.BUILD, KremaBuildTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "Assemble backend and frontend: build the app jar and a separate assets jar into lib/"
        task.configureProject(ext)
        // 编译交回 Gradle：kremaBuild 跑在 compileJava / processResources 之后。
        task.dependsOn(tasks.named("classes"))
        // 前端 dist 是 kremaBuild 的共用前置：声明成依赖后它才成为可缓存、UP-TO-DATE 的产出。
        task.dependsOn(tasks.named(KremaTaskNames.FRONTEND_BUILD))
        // 后端 classpath：主类产出与资源目录（目录形态）+ 运行期依赖 jar（文件形态），都进 app jar。
        task.classpathFiles.from(classpath.classesDirs)
        task.classpathFiles.from(classpath.runtimeClasspath)
        // 前端 dist 打进独立的 assets jar；dev/prod 不分区，整包始终带前端资源。
        task.distFiles.from(
            tasks.named(KremaTaskNames.FRONTEND_BUILD, KremaFrontendBuildTask::class.java).map { it.distDir },
        )
    }

    tasks.register(KremaTaskNames.RUNTIME_IMAGE, KremaRuntimeImageTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "Derive modules via jdeps and trim a minimal runtime image with jlink (build/krema/runtime)"
        task.configureProject(ext)
        task.runtimeImageDir.convention(task.outputDir.dir("runtime"))
        task.jdkBinDir.set(jdkBinDir())
        task.multiRelease.set(targetBytecodeVersion())
        task.classpathFiles.from(classpath.classesDirs)
        task.classpathFiles.from(classpath.runtimeClasspath)
        // 用 convention 而不是 set：扩展侧没写 modules 时 set(provider) 等于显式给一个无值 provider，
        // 非 @Optional 的 @Input 会在校验期就报「没有值」，convention 兜不住这种情况。
        task.modules.convention(ext.runtime.modules.orElse(emptySet()))
        // 模块推导读的是编译产出，镜像因此建立在 classes 之上。
        task.dependsOn(tasks.named("classes"))
    }

    tasks.register(KremaTaskNames.JDEPS_MODULES, KremaJdepsModulesTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "Print the runtime module list derived from jdeps, without trimming an image"
        task.jdkBinDir.set(jdkBinDir())
        task.multiRelease.set(targetBytecodeVersion())
        task.classpathFiles.from(classpath.classesDirs)
        task.classpathFiles.from(classpath.runtimeClasspath)
        // 不 dependsOn("classes") 的话 classesDirs 还不存在，模块推导会悄悄漏掉应用自己的代码。
        task.dependsOn(tasks.named("classes"))
    }

    tasks.register(KremaTaskNames.JDEPS_MODULES_VERBOSE, KremaJdepsModulesVerboseTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "Print per-package jdeps -v dependency details and write a report to build/krema/jdeps-verbose.txt"
        task.jdkBinDir.set(jdkBinDir())
        task.multiRelease.set(targetBytecodeVersion())
        task.classpathFiles.from(classpath.classesDirs)
        task.classpathFiles.from(classpath.runtimeClasspath)
        task.reportFile.convention(
            ext.outputDir.orElse(layout.buildDirectory.dir("krema")).map { it.file("jdeps-verbose.txt") },
        )
        task.dependsOn(tasks.named("classes"))
    }

    // 未压缩、可直接运行的 app-image：所有平台包的共同底座。
    tasks.register(KremaTaskNames.BUNDLE, KremaBundleTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "Build a runnable, uncompressed app-image (build/krema/bundle) with the jlink-trimmed runtime"
        task.configureProject(ext)
        task.outputDir.convention(ext.outputDir.orElse(layout.buildDirectory.dir("krema")).map { it.dir(BUNDLE_DIR_NAME) })
        task.appLibDir.convention(
            tasks.named(KremaTaskNames.BUILD, KremaBuildTask::class.java).flatMap { it.outputDir.dir("lib") },
        )
        task.runtimeImageDir.set(
            tasks
                .named(KremaTaskNames.RUNTIME_IMAGE, KremaRuntimeImageTask::class.java)
                .flatMap { it.runtimeImageDir },
        )
        task.jdkBinDir.set(jdkBinDir())
        task.macIcon.set(ext.bundling.macIcon)
        task.winIcon.set(ext.bundling.winIcon)
        task.linuxIcon.set(ext.bundling.linuxIcon)
        task.dependsOn(tasks.named(KremaTaskNames.BUILD))
        task.dependsOn(tasks.named(KremaTaskNames.RUNTIME_IMAGE))
    }

    // 按需注册平台包格式 task：只注册宿主平台配置了的那些，没配就一张都不出。
    //
    // 必须推迟到 afterEvaluate：用户的 `krema { bundling { } }` 块在插件 apply 之后才执行，
    // 在 apply 里直接读 `formatsForHost()` 只会拿到空集合，于是平台包 task 一张都注册不出来。
    afterEvaluate {
        val hostFormats = ext.bundling.formatsForHost()
        val platformTaskNames =
            hostFormats.map { format ->
                val name = kremaPlatformTaskName(format)
                tasks.register(name, KremaPlatformTask::class.java) { task ->
                    task.group = KremaTaskNames.GROUP
                    task.description = "Build the ${format.id} platform package from the app-image (build/krema/platform/${format.id})"
                    task.configureProject(ext)
                    task.format.set(format)
                    task.outputDir.convention(
                        ext.outputDir.orElse(layout.buildDirectory.dir("krema")).map { it.dir(platformOutputDirName(format.id)) },
                    )
                    // app-image 目录 = kremaBundle 的 outputDir 下按宿主平台命名的子目录。
                    //
                    // 两段都不能在闭包里捕获不可序列化的对象：
                    //  - 子目录名只在配置期由 appName + 平台算一次，存成 String，不在 provider 闭包里调
                    //    `kremaConfig(ext)`（那是 Project 扩展，闭包捕获 Project 会让配置缓存序列化任务图时
                    //    带上 DefaultProject，直接丢弃缓存）。
                    //  - appImageDir 以 `tasks.named(BUNDLE)` 这个「按路径引用」的 task provider 为源，而不是直接
                    //    捕获 bundleTask 这个 Task 对象：后者在闭包里捕获 KremaBundleTask，同样不可序列化。
                    val appImageDirName =
                        Platform.of(System.getProperty("os.name")).appImageDirName(kremaConfig(ext).packageName)
                    task.appImageDir.convention(
                        tasks
                            .named(KremaTaskNames.BUNDLE, KremaBundleTask::class.java)
                            .flatMap { it.outputDir }
                            .map { it.dir(appImageDirName) },
                    )
                    task.jdkBinDir.set(jdkBinDir())
                    task.appImageToolAutoDownload.convention(ext.bundling.appImageToolAutoDownload.orElse(true))
                    task.macIcon.set(ext.bundling.macIcon)
                    task.winIcon.set(ext.bundling.winIcon)
                    task.linuxIcon.set(ext.bundling.linuxIcon)
                    task.dependsOn(tasks.named(KremaTaskNames.BUNDLE))
                }
                name
            }

        // 平台包聚合 task：只把本平台配置了的各格式 task 串起来，本身不产文件；没配格式时无依赖、空跑成功。
        tasks.register(KremaTaskNames.BUNDLE_PLATFORM) { task ->
            task.group = KremaTaskNames.GROUP
            task.description = "Aggregate the host platform's configured format packages (kremaPlatform<format>)"
            platformTaskNames.forEach { task.dependsOn(tasks.named(it)) }
        }
    }

    // 起 classpath 上的应用进程（开发期），注入 KREMA_DEV_URL。
    tasks.register(KremaTaskNames.DEV_RUN, KremaDevRunTask::class.java) { task ->
        task.configureProjectDevRun(ext)
        // classpath 取「编译产物 + 运行期依赖」：source set 的运行时 classpath 才含项目自己的类，
        // 光 runtimeClasspath 只会拿到依赖 jar、主类找不到（ClassNotFoundException）。
        task.classpath = classpath.classesDirs + classpath.runtimeClasspath
        // 主类在配置期就定死（不随执行变）：直接算成 String 设进 convention，不在 provider 闭包里调
        // `task.resolvedMainClass()` —— 那会捕获 task 对象，配置缓存序列化任务图时同样不可序列化。
        task.mainClass.convention(task.resolvedMainClass())
        task.doFirst { task.environment("KREMA_DEV_URL", task.resolvedDevUrl()) }
        // 干净仓库也要先编译：否则 classesDirs 还空着，起的进程照样找不到主类。
        task.dependsOn(tasks.named("classes"))
        // 不依赖 kremaBuild / kremaFrontendBuild：前端由使用方自己的 dev server 提供。
    }

    // 起 kremaBundle 打出的 app-image（产物验证 / 分发预览）。
    registerRun(
        KremaTaskNames.BUNDLE_RUN,
        KremaTaskNames.BUNDLE,
        tasks.named(KremaTaskNames.BUNDLE, KremaBundleTask::class.java).flatMap { it.outputDir },
        ext,
        cleanup,
    )

    tasks.register(KremaTaskNames.SIGNER_GENERATE, KremaSignerGenerateTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "Generate a signing key pair"
        task.configureProject(ext)
        task.privateKey.convention(task.outputDir.file("keys/krema.key"))
    }

    tasks.register(KremaTaskNames.SIGNER_SIGN, KremaSignerSignTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "Sign artifacts with the private key"
        task.configureProject(ext)
    }
}

/**
 * 读 `krema.toml`。
 *
 * 走 `krema { }` 显式指定的路径，没指定才用项目根下的约定位置 —— 与 [KremaConfigTask.configFilePath] 的取法一致。
 * 注册期就要用到配置（如前端产物目录），故这里在配置期直接读文件，而不是等 task 执行。
 */
private fun Project.kremaConfig(ext: KremaExtension): KremaConfig {
    val toml =
        ext.configFile
            .orElse(layout.projectDirectory.file("krema.toml"))
            .get()
            .asFile
            .toPath()
    return KremaConfigLoader().loadOrDefault(toml)
}

/**
 * 接上前端目录的两层取值：`krema.frontend.dir` 是覆盖值，`krema.toml` 的 `frontend_dir` 是底，都没写就是项目根。
 *
 * 后一次 [org.gradle.api.file.DirectoryProperty.convention] 顶掉前一次：Gradle 的 convention 只看这个
 * Property 自己有没有值，所以「扩展侧没赋值」不能靠第二次 convention 表达 —— 与上面前端命令那几处同一形状。
 */
private fun Project.conventionFrontendDir(
    ext: KremaExtension,
    target: org.gradle.api.file.DirectoryProperty,
) {
    // 键缺失时兜成 "."（即项目根自身），而不是在 provider 里判 null：provider 的泛型不带空性标注，
    // map 里判 null 会被当成恒假，而键缺失时它运行时确实是 null。兜在 provider 之外，空性就看得见。
    val tomlDir = providers.provider { kremaConfig(ext).frontend.dir ?: "." }
    target.convention(tomlDir.map { layout.projectDirectory.dir(it) })
    ext.frontend.dir.orNull
        ?.let { target.convention(layout.projectDirectory.dir(it)) }
}

private fun Project.registerRun(
    name: String,
    bundleTask: String,
    bundleDir: Provider<Directory>,
    ext: KremaExtension,
    cleanup: Provider<KremaProcessCleanupService>,
) {
    tasks.register(name, KremaBundleRunTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "Run the application (long-lived, not part of CI gating)"
        task.configureProject(ext)
        // run 认的是 app-image 本体（由 kremaBundle 产出），目录直接取自那张打包 task，保证起的就是刚打出来的那份 app-image。
        task.bundleDir.convention(bundleDir)
        task.target.convention(hostTarget())
        task.cleanupService.set(cleanup)
        task.usesService(cleanup)
        // 先打包再起进程，找不到 bundle 时直接报错而不是凭空 launch。
        task.dependsOn(tasks.named(bundleTask))
    }
}

private fun hostTarget(): String = "${PlatformDetector.detect().name.lowercase()}-${PlatformDetector.getArch()}"

/** 给 `kremaDevRun` 接上 `krema.toml` 读取与主类 / devUrl 覆盖值。 */
private fun KremaDevRunTask.configureProjectDevRun(ext: KremaExtension) {
    configFilePath.convention(
        ext.configFile.orElse(project.layout.projectDirectory.file("krema.toml")).map { it.asFile.absolutePath },
    )
    configFileSetExplicitly.convention(ext.configFile.isPresent)
    configFilePresent.convention(
        project.provider {
            java.nio.file.Files
                .exists(
                    java.nio.file.Path
                        .of(configFilePath.get()),
                )
        },
    )
    mainClassOverride.convention(ext.mainClass)
    devUrlOverride.convention(ext.devUrl)
}

/** 编译所用 JDK 的 `bin` 目录：jlink、jdeps 与 jpackage 跟着编译用的 JDK 走，而不是跟着 PATH 走。 */
private fun Project.jdkBinDir(): Provider<String> {
    val toolchains = extensions.getByType(JavaToolchainService::class.java)
    return toolchains
        .launcherFor { spec ->
            // 不指定语言版本时 launcherFor 取当前 JVM，正是 Gradle 自己的默认行为。
            spec.languageVersion.set(toolchainLanguageVersion())
        }.map {
            it.metadata.installationPath
                .dir("bin")
                .asFile.absolutePath
        }
}

/** 编译所用 JDK 的版本：读 toolchain，没配就是空 —— 这时 `launcherFor` 自己会取当前 JVM。 */
private fun Project.toolchainLanguageVersion(): Provider<JavaLanguageVersion> =
    extensions.getByType(JavaPluginExtension::class.java).toolchain.languageVersion

/**
 * 编译产物的目标字节码主版本号，给 jdeps 的 `--multi-release` 用。
 *
 * 取字节码目标而不是 toolchain：jdeps 拿到的 class 是按目标版本编译出来的，`--multi-release` 要与
 * 它们对齐。toolchain 只说明「用哪个 JDK 编」，两者可以不同（拿 JDK 25 编出目标 17 的产物是常事），
 * 对着 25 去读 17 字节码的 MR-jar 会算出一份用不上的模块清单。
 *
 * 走 `targetCompatibility` 而不是 toolchain：它没配时 Gradle 就取当前 JVM，且配了 toolchain 时
 * 自动跟着 toolchain 走 —— 默认值与「跟着编译走」这两条都成立，而直接读 toolchain 只满足后者，
 * 会在没配 toolchain 的工程里拿不到值，把一条本该有默认值的输入变成硬前置。
 */
private fun Project.targetBytecodeVersion(): Provider<String> =
    providers.provider {
        val target = extensions.getByType(JavaPluginExtension::class.java).targetCompatibility
        // jdeps 只接受 9 及以上（更低的版本号它直接报「选项的参数无效」），而模块化本身也是 9 起的概念。
        if (target < JavaVersion.VERSION_1_9) {
            throw GradleException("目标字节码 $target 低于 9，推导不出 JDK 模块；请把 targetCompatibility 提到 9 及以上")
        }
        target.majorVersion
    }
