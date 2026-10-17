/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.of
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerIfAbsent
import top.birthcat.kremaf.ext.gradle.config.ASSETS_DIR
import top.birthcat.kremaf.ext.gradle.config.BUNDLE_DIR_NAME
import top.birthcat.kremaf.ext.gradle.config.KremaConfigValueSource
import top.birthcat.kremaf.ext.gradle.config.KremaExtension
import top.birthcat.kremaf.ext.gradle.config.PROCESS_CLEANUP_SERVICE
import top.birthcat.kremaf.ext.gradle.config.ResolvedKremaConfig
import top.birthcat.kremaf.ext.gradle.config.appJarName
import top.birthcat.kremaf.ext.gradle.config.assetsJarName
import top.birthcat.kremaf.ext.gradle.config.platformOutputDirName
import top.birthcat.kremaf.ext.gradle.domain.KremaApplicationClasspath
import top.birthcat.kremaf.ext.gradle.domain.PlatformDetector
import top.birthcat.kremaf.ext.gradle.domain.kremaApplicationClasspath
import top.birthcat.kremaf.ext.gradle.task.KremaBundleRunTask
import top.birthcat.kremaf.ext.gradle.task.KremaBundleTask
import top.birthcat.kremaf.ext.gradle.task.KremaFrontendBuildTask
import top.birthcat.kremaf.ext.gradle.task.KremaInitTask
import top.birthcat.kremaf.ext.gradle.task.KremaJdepsModulesTask
import top.birthcat.kremaf.ext.gradle.task.KremaJdepsModulesVerboseTask
import top.birthcat.kremaf.ext.gradle.task.KremaPlatformTask
import top.birthcat.kremaf.ext.gradle.task.KremaRuntimeImageTask
import top.birthcat.kremaf.ext.gradle.task.KremaSignerGenerateTask
import top.birthcat.kremaf.ext.gradle.task.KremaSignerSignTask

internal fun Project.registerKremaTasks() {
    val ext = extensions.getByType(KremaExtension::class)

    // krema.toml 全构建只解析一次：ValueSource 每次构建 obtain 一次，返回值即配置缓存的跟踪键。
    val kremaConfig =
        providers.of(KremaConfigValueSource::class) {
            parameters.configFilePath.set(
                ext.configFile.orElse(layout.projectDirectory.file("krema.toml")).map { it.asFile.absolutePath },
            )
            // isPresent 没有 provider 形态，map{true}.orElse(false) 表达同一件事且保持惰性：
            // 这里的代码在 apply 期跑，krema { } 块还没执行，eager 读只会恒为 false。
            parameters.configFileSetExplicitly.set(ext.configFile.map { true }.orElse(false))
        }
    // 单一裁决点：ext 覆盖 toml 的优先级算清一次，各 task 在注册点 bind 它的 provider。
    val resolvedConfig = ResolvedKremaConfig(ext, kremaConfig, layout.projectDirectory, layout.buildDirectory)
    gradle.sharedServices.registerIfAbsent(PROCESS_CLEANUP_SERVICE, KremaProcessCleanupService::class) {}

    tasks.register<KremaInitTask>(KremaTaskNames.INIT) {
        projectDir.convention(layout.projectDirectory)
        outputDir.convention(layout.projectDirectory)
    }

    val frontendBuildTask =
        tasks.register<KremaFrontendBuildTask>(KremaTaskNames.FRONTEND_BUILD) {
            resolvedConfig.configureFrontEndBuild(this)

            frontendInputs.from(
                project.fileTree(inputFrontendDir) {
                    include(
                        "**/*.vue",
                        "**/*.ts",
                        "**/*.js",
                        "**/*.tsx",
                        "**/*.jsx",
                        "*.lock",
                        "**/*.html",
                        "**/*.css",
                        "**/*.png",
                        "**/*.jpg",
                        "**/*.webp",
                        "**/*.gif",
                        "**/*.svg",
                    )
                },
            )
        }

    // classes + 运行期 classpath：kremaBuild、运行时镜像与依赖分析共用。
    val appClasspath = kremaApplicationClasspath()
    // 两个 jar 必须顶层注册：register 闭包懒执行，跑到时 task set 已冻结，register 直接报错。
    // classes 显式依赖：jar 的 from 是输出目录位置，不声明看不到产出它的 processResources。
    val classesTask = tasks.named("classes")
    val appJarTask =
        tasks.register<Jar>(KremaTaskNames.APP_JAR) {
            dependsOn(classesTask)
            from(appClasspath.classesDirs)
            appClasspath.resourcesDir?.let { from(it) }
            manifest { attributes(mapOf("Main-Class" to resolvedConfig.mainClass.get())) }
            archiveFileName.set(resolvedConfig.appName.map { appJarName(it) })
        }

    val assetsJarTask =
        tasks.register<Jar>(KremaTaskNames.ASSETS_JAR) {
            dependsOn(frontendBuildTask)
            from(resolvedConfig.frontendOutDir) {
                into(ASSETS_DIR)
            }
            archiveFileName.set(resolvedConfig.appName.map { assetsJarName(it) })
        }

    // 聚合任务
    tasks.register(KremaTaskNames.BUILD) {
        dependsOn(appJarTask, assetsJarTask)
    }

    val runtimeTask =
        tasks.register<KremaRuntimeImageTask>(KremaTaskNames.RUNTIME_IMAGE) {
            dependsOn(tasks.named("classes"))
            classpathFiles.from(appClasspath.classesDirs, appClasspath.runtimeClasspath)
            resolvedConfig.configureRuntimeImage(this)
        }

    tasks.register<KremaJdepsModulesTask>(KremaTaskNames.JDEPS_MODULES) {
        dependsOn(tasks.named("classes"))
        configureJdepsModules(appClasspath)
    }

    tasks.register<KremaJdepsModulesVerboseTask>(KremaTaskNames.JDEPS_MODULES_VERBOSE) {
        dependsOn(tasks.named("classes"))
        classpathFiles.from(appClasspath.classesDirs, appClasspath.runtimeClasspath)
        configureJepsModulesVerbose(this, resolvedConfig)
    }

    val bundleTask =
        tasks.register<KremaBundleTask>(KremaTaskNames.BUNDLE) {
            dependsOn(appJarTask, assetsJarTask, runtimeTask)
            inputJarDeps.from(
                appJarTask.map { it.archiveFile },
                assetsJarTask.map { it.archiveFile },
                appClasspath.runtimeClasspath,
            )
            configureBundle(this, resolvedConfig, runtimeTask)
        }

    // 按需注册平台包：只注册宿主平台配了的格式，没配就一种都不出。
    // 必须推迟到 afterEvaluate：krema { bundling { } } 在 apply 之后才执行，
    // apply 里读 formatsForHost() 只会拿到空集合。
    afterEvaluate {
        val hostFormats = ext.bundling.formatsForHost()
        val platformTaskNames =
            hostFormats.map { platformFormat ->
                val name = platformFormat.kremaPlatformTaskName
                tasks.register<KremaPlatformTask>(name) {
                    // 描述随各格式而定，挂在注册点；group 已落到 KremaPlatformTask 的 init。
                    description =
                        "Build the ${platformFormat.id} platform package from the app-image (build/krema/platform/${platformFormat.id})"
                    dependsOn(bundleTask)
                    outputDir.convention(resolvedConfig.outputDir.map { it.dir(platformOutputDirName(platformFormat.id)) })
                    val settings = resolvedConfig.bundleSettings
                    bundleSettings.convention(settings)
                    format.set(platformFormat)
                    appImageDir.convention(resolvedConfig.bundleDir.zip(resolvedConfig.appName) { bundleDir, appName ->
                        bundleDir.dir(appName)
                    })
                    jdkBinDir.set(jdkBinDir())
                    appImageToolAutoDownload.convention(ext.bundling.appImageToolAutoDownload.orElse(true))
                }
                name
            }

        // 聚合本平台配置的各格式 task；没配格式时为空跑成功。
        tasks.register(KremaTaskNames.BUNDLE_PLATFORM) {
            group = KremaTaskNames.GROUP
            description = "Aggregate the host platform's configured format packages (kremaPlatform<format>)"
            platformTaskNames.forEach { dependsOn(tasks.named(it)) }
        }
    }

    tasks.register<JavaExec>(KremaTaskNames.DEV_RUN) {
        group = KremaTaskNames.GROUP
        description = "Run the backend app on the classpath, injecting KREMA_DEV_URL; long-lived, not part of CI gating"
        // 干净仓库也要先编译：否则 classesDirs 还空着，起的进程照样找不到主类。
        dependsOn(tasks.named("classes"))

        // 取编译产物 + 运行期依赖：光 runtimeClasspath 不含项目自己的类，
        // 起的进程会 ClassNotFound 找不到主类。
        classpath = appClasspath.classesDirs + appClasspath.runtimeClasspath
        // 主类配置期算成 String 设进 convention：provider 闭包里调
        // task.resolvedMainClass() 会捕获 task 对象，配置缓存不可序列化。
        mainClass.convention(resolvedConfig.mainClass)
        doFirst { environment("KREMA_DEV_URL", resolvedConfig.devUrl.get()) }
        // 不依赖 kremaBuild/FrontendBuild：前端由使用方自己的 dev server 提供。
    }

    tasks.register<KremaBundleRunTask>(KremaTaskNames.BUNDLE_RUN) {
        // 先打包再起进程，找不到 bundle 时直接报错而不是凭空 launch。
        dependsOn(bundleTask)
        // bundleDir 取自 kremaBundle 刚打出的产物，保证起的就是那一份。
        bundleDir.convention(bundleTask.flatMap { it.outputBundleDir })
        bundleSettings.convention(resolvedConfig.bundleSettings)
        target.convention(hostTarget())
    }

    tasks.register<KremaSignerGenerateTask>(KremaTaskNames.SIGNER_GENERATE) {
        outputDir.convention(resolvedConfig.outputDir)
        privateKey.convention(outputDir.file("keys/krema.key"))
    }

    tasks.register<KremaSignerSignTask>(KremaTaskNames.SIGNER_SIGN) {
        outputDir.convention(resolvedConfig.outputDir)
    }
}

private fun Project.configureBundle(
    task: KremaBundleTask,
    resolvedConfig: ResolvedKremaConfig,
    runtimeTask: TaskProvider<KremaRuntimeImageTask>,
) {
    task.buildDir.convention(resolvedConfig.outputDir)
    task.outputBundleDir.convention(resolvedConfig.bundleDir)
    task.bundleSettings.convention(resolvedConfig.bundleSettings)
    task.inputRuntimeImageDir.convention(runtimeTask.flatMap { it.outputRuntimeImageDir })
    task.jdkBinDir.set(jdkBinDir())
    task.hostPlatform.set(PlatformDetector.detect())
}

private fun ResolvedKremaConfig.bundleDir(resolvedConfig: ResolvedKremaConfig): Provider<Directory> =
    resolvedConfig.outputDir.map { it.dir(BUNDLE_DIR_NAME) }

private fun Project.configureJepsModulesVerbose(
    task: KremaJdepsModulesVerboseTask,
    resolvedConfig: ResolvedKremaConfig,
) {
    task.apply {
        jdkBinDir.set(jdkBinDir())
        multiRelease.set(targetBytecodeVersion)
        reportFile.convention(
            resolvedConfig.outputDir.map { it.file("jdeps-verbose.txt") },
        )
    }
}

private fun KremaJdepsModulesTask.configureJdepsModules(classpath: KremaApplicationClasspath) {
    jdkBinDir.set(project.jdkBinDir())
    multiRelease.set(project.targetBytecodeVersion)
    classpathFiles.from(classpath.classesDirs)
    classpathFiles.from(classpath.runtimeClasspath)
}

private fun ResolvedKremaConfig.configureRuntimeImage(task: KremaRuntimeImageTask) {
    task.apply {
        outputRuntimeImageDir.convention(outputDir.map { it.dir("runtime") })
        jdkBinDir.set(project.jdkBinDir())
        hostPlatform.set(PlatformDetector.detect())
        multiRelease.set(project.targetBytecodeVersion)
        inputModules.convention(runtimeModules.orElse(emptySet()))
    }
}

private fun ResolvedKremaConfig.configureFrontEndBuild(task: KremaFrontendBuildTask) {
    task.apply {
        outputFrontendDir.convention(frontendOutDir)
        inputBuildCommand.convention(buildCommand)
        inputInstallCommand.convention(installCommand)
        inputFrontendDir.convention(frontendDir)
    }
}

private fun hostTarget() = "${PlatformDetector.detect().name.lowercase()}-${PlatformDetector.getArch()}"

/** 编译所用 JDK 的 bin 目录：工具跟着编译 JDK 走，不跟 PATH。 */
private fun Project.jdkBinDir() =
    extensions
        .getByType(JavaToolchainService::class)
        .launcherFor {
            // 不指定语言版本时 launcherFor 取当前 JVM，正是 Gradle 自己的默认行为。
            languageVersion.set(toolchainLanguageVersion)
        }.map {
            it.metadata.installationPath
                .dir("bin")
                .asFile.absolutePath
        }

/** 编译 JDK 版本：读 toolchain，没配就是空（launcherFor 自取当前 JVM）。 */
private val Project.toolchainLanguageVersion
    get() = extensions.getByType(JavaPluginExtension::class).toolchain.languageVersion

/** 目标字节码主版本（jdeps --multi-release 用）：取 targetCompatibility。 */
private val Project.targetBytecodeVersion
    get() =
        toolchainLanguageVersion.map { it.asInt().toString() }
