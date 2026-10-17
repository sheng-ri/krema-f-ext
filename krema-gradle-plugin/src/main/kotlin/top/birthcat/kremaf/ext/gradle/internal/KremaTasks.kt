/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.of
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerIfAbsent
import top.birthcat.kremaf.ext.gradle.api.KremaExtension
import top.birthcat.kremaf.ext.gradle.api.KremaProcessCleanupService
import top.birthcat.kremaf.ext.gradle.api.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.api.Platform
import top.birthcat.kremaf.ext.gradle.api.task.KremaBundleRunTask
import top.birthcat.kremaf.ext.gradle.api.task.KremaBundleTask
import top.birthcat.kremaf.ext.gradle.api.task.KremaFrontendBuildTask
import top.birthcat.kremaf.ext.gradle.api.task.KremaInitTask
import top.birthcat.kremaf.ext.gradle.api.task.KremaJdepsModulesTask
import top.birthcat.kremaf.ext.gradle.api.task.KremaJdepsModulesVerboseTask
import top.birthcat.kremaf.ext.gradle.api.task.KremaPlatformTask
import top.birthcat.kremaf.ext.gradle.api.task.KremaRuntimeImageTask
import top.birthcat.kremaf.ext.gradle.api.task.KremaSignerGenerateTask
import top.birthcat.kremaf.ext.gradle.api.task.KremaSignerSignTask
import top.birthcat.kremaf.ext.gradle.internal.config.ASSETS_DIR
import top.birthcat.kremaf.ext.gradle.internal.config.DEV_URL_ENV
import top.birthcat.kremaf.ext.gradle.internal.config.JDEPS_VERBOSE_REPORT_NAME
import top.birthcat.kremaf.ext.gradle.internal.config.KREMA_CONFIG_FILE_NAME
import top.birthcat.kremaf.ext.gradle.internal.config.KremaConfigValueSource
import top.birthcat.kremaf.ext.gradle.internal.config.LIB_DIR_NAME
import top.birthcat.kremaf.ext.gradle.internal.config.PROCESS_CLEANUP_SERVICE
import top.birthcat.kremaf.ext.gradle.internal.config.RUNTIME_DIR_NAME
import top.birthcat.kremaf.ext.gradle.internal.config.ResolvedKremaConfig
import top.birthcat.kremaf.ext.gradle.internal.config.appJarName
import top.birthcat.kremaf.ext.gradle.internal.config.assetsJarName
import top.birthcat.kremaf.ext.gradle.internal.config.platformOutputDirName
import top.birthcat.kremaf.ext.gradle.internal.domain.KremaApplicationClasspath
import top.birthcat.kremaf.ext.gradle.internal.domain.PlatformDetector
import top.birthcat.kremaf.ext.gradle.internal.domain.kremaApplicationClasspath

/** 注册全部 Krema task，插件 apply 时调用一次。 */
internal fun Project.registerKremaTasks() {
    val ext = extensions.getByType(KremaExtension::class)

    // 全构建只解析一次 toml；ValueSource 的返回值即配置缓存键。
    val kremaConfig =
        providers.of(KremaConfigValueSource::class) {
            parameters.configFilePath.set(
                ext.configFile
                    .orElse(layout.projectDirectory.file(KREMA_CONFIG_FILE_NAME))
                    .map { it.asFile.absolutePath },
            )
            // isPresent 无 provider 形态；apply 期读恒为 false，故用 map 保惰性。
            parameters.configFileSetExplicitly.set(ext.configFile.map { true }.orElse(false))
        }

    val resolvedConfig = ResolvedKremaConfig(ext, kremaConfig, layout.projectDirectory, layout.buildDirectory)

    // java_source_dir 作为额外源码根挂到 main；默认的 src/main/java 保持不动。
    extensions
        .getByType(JavaPluginExtension::class)
        .sourceSets
        .getByName("main")
        .java
        .srcDir(resolvedConfig.javaSourceDir)
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

    // classes 与运行期 classpath：打包、运行时镜像与 jdeps 共用。
    val appClasspath = kremaApplicationClasspath()
    // jar 必须顶层注册：register 闭包执行时 task 集已冻结。
    val classesTask = tasks.named("classes")
    val libDir = resolvedConfig.outputDir.map { it.dir(LIB_DIR_NAME) }
    val appJarTask =
        tasks.register<Jar>(KremaTaskNames.APP_JAR) {
            dependsOn(classesTask)
            from(appClasspath.classesDirs)
            appClasspath.resourcesDir?.let { from(it) }
            manifest { attributes(mapOf("Main-Class" to resolvedConfig.mainClass.get())) }
            archiveFileName.set(resolvedConfig.appName.map { appJarName(it) })
            destinationDirectory.set(libDir)
        }

    val assetsJarTask =
        tasks.register<Jar>(KremaTaskNames.ASSETS_JAR) {
            dependsOn(frontendBuildTask)
            from(resolvedConfig.frontendOutDir) {
                into(ASSETS_DIR)
            }
            archiveFileName.set(resolvedConfig.appName.map { assetsJarName(it) })
            destinationDirectory.set(libDir)
        }

    // 依赖 jar 与自家 jar 同落 lib/：bundle 只认这一个目录，不必再另抄一份 deps。
    // 用 Copy 而非 Sync：Sync 会删掉 lib/ 里不来自它这一源的 jar，把 app jar 一起清掉。
    val libTask =
        tasks.register<Copy>(KremaTaskNames.LIB) {
            dependsOn(appJarTask, assetsJarTask)
            from(appClasspath.runtimeClasspath)
            into(libDir)
        }

    tasks.register(KremaTaskNames.BUILD) {
        group = KremaTaskNames.GROUP
        description = "Aggregate the app jar, the assets jar and the runtime dependency jars into lib/"
        dependsOn(libTask)
    }

    val runtimeTask =
        tasks.register<KremaRuntimeImageTask>(KremaTaskNames.RUNTIME_IMAGE) {
            dependsOn(classesTask)
            classpathFiles.from(appClasspath.classesDirs, appClasspath.runtimeClasspath)
            resolvedConfig.configureRuntimeImage(this)
        }

    tasks.register<KremaJdepsModulesTask>(KremaTaskNames.JDEPS_MODULES) {
        dependsOn(classesTask)
        configureJdepsModules(appClasspath)
    }

    tasks.register<KremaJdepsModulesVerboseTask>(KremaTaskNames.JDEPS_MODULES_VERBOSE) {
        dependsOn(classesTask)
        classpathFiles.from(appClasspath.classesDirs, appClasspath.runtimeClasspath)
        resolvedConfig.configureJdepsModulesVerbose(this)
    }

    val bundleTask =
        tasks.register<KremaBundleTask>(KremaTaskNames.BUNDLE) {
            dependsOn(libTask, runtimeTask)
            inputJarDeps.from(libDir)
            resolvedConfig.configureBundle(this, runtimeTask)
        }

    // 平台包按需注册：只注册宿主平台配了的格式。
    // 必须等 afterEvaluate：bundling { } 在 apply 之后才执行。
    afterEvaluate {
        val platformTaskNames =
            ext.bundling.formatsForHost().map { platformFormat ->
                val name = platformFormat.kremaPlatformTaskName
                tasks.register<KremaPlatformTask>(name) {
                    description =
                        "Build the ${platformFormat.id} platform package from the app-image (build/krema/platform/${platformFormat.id})"
                    dependsOn(bundleTask)
                    outputDir.convention(resolvedConfig.outputDir.map { it.dir(platformOutputDirName(platformFormat.id)) })
                    bundleSettings.convention(resolvedConfig.bundleSettings)
                    format.set(platformFormat)
                    // --win-* 只有 Windows 安装器认，别的格式不挂，免得白占缓存键。
                    if (platformFormat.platform == Platform.WINDOWS) {
                        windowsOptions.convention(resolvedConfig.windowsInstallerOptions)
                    }
                    appImageDir.convention(
                        resolvedConfig.bundleDir.zip(resolvedConfig.appName) { dir, appName -> dir.dir(appName) },
                    )
                    jdkBinDir.set(jdkBinDir())
                    appImageToolAutoDownload.convention(ext.bundling.appImageToolAutoDownload.orElse(true))
                }
                name
            }

        // 聚合本平台各格式；没配格式时为空跑成功。
        tasks.register(KremaTaskNames.BUNDLE_PLATFORM) {
            group = KremaTaskNames.GROUP
            description = "Aggregate the host platform's configured format packages (kremaPlatform<format>)"
            platformTaskNames.forEach { dependsOn(tasks.named(it)) }
        }
    }

    tasks.register<JavaExec>(KremaTaskNames.DEV_RUN) {
        group = KremaTaskNames.GROUP
        description = "Run the backend app on the classpath, injecting KREMA_DEV_URL; long-lived, not part of CI gating"
        // 干净仓库也要先编译，否则 classpath 上找不到主类。
        dependsOn(classesTask)

        // 光 runtimeClasspath 不含项目自己的类，必须叠加 classesDirs。
        classpath = appClasspath.classesDirs + appClasspath.runtimeClasspath
        // 配置期设成 String：provider 闭包捕获 task 会破坏配置缓存。
        mainClass.convention(resolvedConfig.mainClass)
        doFirst { environment(DEV_URL_ENV, resolvedConfig.devUrl.get()) }
        // 不依赖前端构建：dev 期由使用方自己的 dev server 提供。
    }

    tasks.register<KremaBundleRunTask>(KremaTaskNames.BUNDLE_RUN) {
        dependsOn(bundleTask)
        // bundle 根目录；task 内再拼 appName 得到 app-image 目录。
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

private fun ResolvedKremaConfig.configureBundle(
    task: KremaBundleTask,
    runtimeTask: TaskProvider<KremaRuntimeImageTask>,
) {
    task.buildDir.convention(outputDir)
    task.outputBundleDir.convention(bundleDir)
    task.bundleSettings.convention(bundleSettings)
    task.inputRuntimeImageDir.convention(runtimeTask.flatMap { it.outputRuntimeImageDir })
    task.jdkBinDir.set(task.project.jdkBinDir())
    task.hostPlatform.set(PlatformDetector.detect())
}

private fun ResolvedKremaConfig.configureJdepsModulesVerbose(task: KremaJdepsModulesVerboseTask) {
    task.jdkBinDir.set(task.project.jdkBinDir())
    task.multiRelease.set(task.project.targetBytecodeVersion)
    task.reportFile.convention(outputDir.map { it.file(JDEPS_VERBOSE_REPORT_NAME) })
}

private fun KremaJdepsModulesTask.configureJdepsModules(classpath: KremaApplicationClasspath) {
    jdkBinDir.set(project.jdkBinDir())
    multiRelease.set(project.targetBytecodeVersion)
    classpathFiles.from(classpath.classesDirs)
    classpathFiles.from(classpath.runtimeClasspath)
}

private fun ResolvedKremaConfig.configureRuntimeImage(task: KremaRuntimeImageTask) {
    task.outputRuntimeImageDir.convention(outputDir.map { it.dir(RUNTIME_DIR_NAME) })
    task.jdkBinDir.set(task.project.jdkBinDir())
    task.hostPlatform.set(PlatformDetector.detect())
    task.multiRelease.set(task.project.targetBytecodeVersion)
    task.inputModules.convention(runtimeModules.orElse(emptySet()))
}

private fun ResolvedKremaConfig.configureFrontEndBuild(task: KremaFrontendBuildTask) {
    task.outputFrontendDir.convention(frontendOutDir)
    task.inputBuildCommand.convention(buildCommand)
    task.inputInstallCommand.convention(installCommand)
    task.inputFrontendDir.convention(frontendDir)
}

private fun hostTarget() = "${PlatformDetector.detect().name.lowercase()}-${PlatformDetector.getArch()}"

/** 编译所用 JDK 的 bin 目录：工具跟着编译 JDK 走，不跟 PATH。 */
private fun Project.jdkBinDir() =
    extensions
        .getByType(JavaToolchainService::class)
        .launcherFor {
            // 不指定版本时 launcherFor 取当前 JVM，与 Gradle 默认一致。
            languageVersion.set(toolchainLanguageVersion)
        }.map {
            it.metadata.installationPath
                .dir("bin")
                .asFile.absolutePath
        }

/** 编译 JDK 版本；未配置时为空，launcherFor 自取当前 JVM。 */
private val Project.toolchainLanguageVersion
    get() = extensions.getByType(JavaPluginExtension::class).toolchain.languageVersion

/** 目标字节码主版本，供 jdeps 的 `--multi-release` 使用。 */
private val Project.targetBytecodeVersion
    get() = toolchainLanguageVersion.map { it.asInt().toString() }
