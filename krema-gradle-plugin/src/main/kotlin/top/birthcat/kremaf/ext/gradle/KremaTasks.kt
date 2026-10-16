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
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import top.birthcat.kremaf.ext.gradle.config.ASSETS_DIR
import top.birthcat.kremaf.ext.gradle.config.BUNDLE_DIR_NAME
import top.birthcat.kremaf.ext.gradle.config.KremaConfigValueSource
import top.birthcat.kremaf.ext.gradle.config.KremaExtension
import top.birthcat.kremaf.ext.gradle.config.ResolvedKremaConfig
import top.birthcat.kremaf.ext.gradle.config.appJarName
import top.birthcat.kremaf.ext.gradle.config.assetsJarName
import top.birthcat.kremaf.ext.gradle.config.kremaOutputDir
import top.birthcat.kremaf.ext.gradle.config.platformOutputDirName
import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.PlatformDetector
import top.birthcat.kremaf.ext.gradle.domain.appImageDirName
import top.birthcat.kremaf.ext.gradle.domain.kremaApplicationClasspath
import top.birthcat.kremaf.ext.gradle.domain.kremaPlatformTaskName
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

    // krema.toml 全构建只解析一次：ValueSource 每次构建 obtain 一次，返回值即配置缓存的跟踪键。
    val kremaConfig =
        providers.of(KremaConfigValueSource::class.java) { spec ->
            spec.parameters.configFilePath.set(
                ext.configFile.orElse(layout.projectDirectory.file("krema.toml")).map { it.asFile.absolutePath },
            )
            // isPresent 没有 provider 形态，map{true}.orElse(false) 表达同一件事且保持惰性：
            // 这里的代码在 apply 期跑，krema { } 块还没执行，eager 读只会恒为 false。
            spec.parameters.configFileSetExplicitly.set(ext.configFile.map { true }.orElse(false))
        }
    // 单一裁决点：ext 覆盖 toml 的优先级算清一次，各 task 在注册点 bind 它的 provider。
    val resolved = ResolvedKremaConfig(ext, kremaConfig, layout.projectDirectory)
    val buildDir = layout.buildDirectory

    val cleanup =
        gradle.sharedServices.registerIfAbsent("kremaProcessCleanup", KremaProcessCleanupService::class.java) {}

    tasks.register(KremaTaskNames.INIT, KremaInitTask::class.java) { task ->
        task.projectDir.convention(layout.projectDirectory)
        task.outputDir.convention(layout.projectDirectory)
    }

    tasks.register(KremaTaskNames.FRONTEND_BUILD, KremaFrontendBuildTask::class.java) { task ->
        // out_dir 是前端工具链自己的落点，插件只能按它读：读错地方 prod 的 app jar 就空 assets。
        task.outputDir.convention(kremaOutputDir(ext, buildDir))
        task.buildCommand.convention(resolved.buildCommand)
        task.installCommand.convention(resolved.installCommand)
        task.frontendDir.convention(resolved.frontendDir)
        task.distDir.convention(task.frontendDir.flatMap { front -> resolved.outDir.map { front.dir(it) } })
        task.frontendInputs.from(
            listOf("package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml")
                .map { task.frontendDir.dir(it) },
        )
    }

    // classes + 运行期 classpath：kremaBuild、运行时镜像与依赖分析共用。
    val classpath = kremaApplicationClasspath()

    // 两个 jar 必须顶层注册：register 闭包懒执行，跑到时 task set 已冻结，register 直接报错。
    // classes 显式依赖：jar 的 from 是输出目录位置，不声明看不到产出它的 processResources。
    val classesTask = tasks.named("classes")
    val appJarTask =
        tasks.register(KremaTaskNames.APP_JAR, Jar::class.java) { jar ->
            jar.dependsOn(classesTask)
            jar.from(classpath.classesDirs)
            classpath.resourcesDir?.let { jar.from(it) }
            jar.manifest { it.attributes(mapOf("Main-Class" to resolved.mainClass.get())) }
            jar.archiveFileName.set(resolved.appName.map { appJarName(it) })
        }

    val assetsJarTask =
        tasks.register(KremaTaskNames.ASSETS_JAR, Jar::class.java) { jar ->
            jar.from(
                tasks
                    .named(KremaTaskNames.FRONTEND_BUILD, KremaFrontendBuildTask::class.java)
                    .map { it.distDir },
            ) { it.into(ASSETS_DIR) }
            jar.archiveFileName.set(resolved.appName.map { assetsJarName(it) })
        }

    tasks.register(KremaTaskNames.BUILD, KremaBuildTask::class.java) { task ->
        task.outputDir.convention(kremaOutputDir(ext, buildDir))
        task.appJar.set(appJarTask.flatMap { it.archiveFile })
        task.assetsJar.set(assetsJarTask.flatMap { it.archiveFile })
        // 仅收集依赖 jar
        task.dependencyJars.from(classpath.runtimeClasspath)
    }

    tasks.register(KremaTaskNames.RUNTIME_IMAGE, KremaRuntimeImageTask::class.java) { task ->
        task.outputDir.convention(kremaOutputDir(ext, buildDir))
        // 模块推导读的是编译产出，镜像因此建立在 classes 之上。
        task.dependsOn(tasks.named("classes"))
        task.runtimeImageDir.convention(task.outputDir.dir("runtime"))
        task.jdkBinDir.set(jdkBinDir())
        task.hostPlatform.set(PlatformDetector.detect())
        task.multiRelease.set(targetBytecodeVersion())
        task.classpathFiles.from(classpath.classesDirs)
        task.classpathFiles.from(classpath.runtimeClasspath)
        // convention 而非 set：扩展没写 modules 时 set(provider) 是无值 provider，
        // 非 @Optional 的 @Input 校验期就报「没有值」，convention 才兜得住。
        task.modules.convention(ext.runtime.modules.orElse(emptySet()))
    }

    tasks.register(KremaTaskNames.JDEPS_MODULES, KremaJdepsModulesTask::class.java) { task ->
        // 不依赖 classes 的话 classesDirs 不存在，推导会漏掉应用自己的代码。
        task.dependsOn(tasks.named("classes"))
        task.jdkBinDir.set(jdkBinDir())
        task.multiRelease.set(targetBytecodeVersion())
        task.classpathFiles.from(classpath.classesDirs)
        task.classpathFiles.from(classpath.runtimeClasspath)
    }

    tasks.register(KremaTaskNames.JDEPS_MODULES_VERBOSE, KremaJdepsModulesVerboseTask::class.java) { task ->
        task.dependsOn(tasks.named("classes"))
        task.jdkBinDir.set(jdkBinDir())
        task.multiRelease.set(targetBytecodeVersion())
        task.classpathFiles.from(classpath.classesDirs)
        task.classpathFiles.from(classpath.runtimeClasspath)
        task.reportFile.convention(
            ext.outputDir.orElse(layout.buildDirectory.dir("krema")).map { it.file("jdeps-verbose.txt") },
        )
    }

    tasks.register(KremaTaskNames.BUNDLE, KremaBundleTask::class.java) { task ->
        task.outputDir.convention(kremaOutputDir(ext, buildDir).map { it.dir(BUNDLE_DIR_NAME) })
        task.bundleSettings.convention(task.outputDir.map { resolved.bundleSettings(it.asFile.toPath()).get() })
        task.dependsOn(tasks.named(KremaTaskNames.BUILD))
        task.dependsOn(tasks.named(KremaTaskNames.RUNTIME_IMAGE))
        task.appLibDir.convention(
            tasks.named(KremaTaskNames.BUILD, KremaBuildTask::class.java).flatMap { it.outputDir.dir("lib") },
        )
        task.runtimeImageDir.set(
            tasks
                .named(KremaTaskNames.RUNTIME_IMAGE, KremaRuntimeImageTask::class.java)
                .flatMap { it.runtimeImageDir },
        )
        task.jdkBinDir.set(jdkBinDir())
        task.hostPlatform.set(PlatformDetector.detect())
        task.macIcon.set(ext.bundling.macIcon)
        task.winIcon.set(ext.bundling.winIcon)
        task.linuxIcon.set(ext.bundling.linuxIcon)
    }

    // 按需注册平台包：只注册宿主平台配了的格式，没配就一种都不出。
    // 必须推迟到 afterEvaluate：krema { bundling { } } 在 apply 之后才执行，
    // apply 里读 formatsForHost() 只会拿到空集合。
    afterEvaluate {
        val hostFormats = ext.bundling.formatsForHost()
        val platformTaskNames =
            hostFormats.map { format ->
                val name = kremaPlatformTaskName(format)
                tasks.register(name, KremaPlatformTask::class.java) { task ->
                    // 描述随各格式而定，挂在注册点；group 已落到 KremaPlatformTask 的 init。
                    task.description = "Build the ${format.id} platform package from the app-image (build/krema/platform/${format.id})"
                    task.outputDir.convention(kremaOutputDir(ext, buildDir).map { it.dir(platformOutputDirName(format.id)) })
                    task.bundleSettings.convention(task.outputDir.map { resolved.bundleSettings(it.asFile.toPath()).get() })
                    task.dependsOn(tasks.named(KremaTaskNames.BUNDLE))
                    task.format.set(format)
                    // app-image 目录 = kremaBundle 产物下按平台命名的子目录。
                    // 子目录名在 map 闭包里现取：配置期捕获 Project 是官方允许的
                    // （provider 由配置缓存托管），预计算反而多一次解析。
                    val appImageDirName = resolved.appName.map { Platform.of(System.getProperty("os.name")).appImageDirName(it) }
                    task.appImageDir.convention(
                        tasks
                            .named(KremaTaskNames.BUNDLE, KremaBundleTask::class.java)
                            .flatMap { it.outputDir }
                            .flatMap { it.dir(appImageDirName) },
                    )
                    task.jdkBinDir.set(jdkBinDir())
                    task.hostPlatform.set(PlatformDetector.detect())
                    task.appImageToolAutoDownload.convention(ext.bundling.appImageToolAutoDownload.orElse(true))
                    task.macIcon.set(ext.bundling.macIcon)
                    task.winIcon.set(ext.bundling.winIcon)
                    task.linuxIcon.set(ext.bundling.linuxIcon)
                }
                name
            }

        // 聚合本平台配置的各格式 task；没配格式时为空跑成功。
        tasks.register(KremaTaskNames.BUNDLE_PLATFORM) { task ->
            task.group = KremaTaskNames.GROUP
            task.description = "Aggregate the host platform's configured format packages (kremaPlatform<format>)"
            platformTaskNames.forEach { task.dependsOn(tasks.named(it)) }
        }
    }

    tasks.register(KremaTaskNames.DEV_RUN, KremaDevRunTask::class.java) { task ->
        // 干净仓库也要先编译：否则 classesDirs 还空着，起的进程照样找不到主类。
        task.dependsOn(tasks.named("classes"))
        // 取编译产物 + 运行期依赖：光 runtimeClasspath 不含项目自己的类，
        // 起的进程会 ClassNotFound 找不到主类。
        task.classpath = classpath.classesDirs + classpath.runtimeClasspath
        // 主类配置期算成 String 设进 convention：provider 闭包里调
        // task.resolvedMainClass() 会捕获 task 对象，配置缓存不可序列化。
        task.mainClass.convention(resolved.mainClass)
        task.doFirst { task.environment("KREMA_DEV_URL", resolved.devUrl.get()) }
        // 不依赖 kremaBuild/FrontendBuild：前端由使用方自己的 dev server 提供。
    }

    tasks.register(KremaTaskNames.BUNDLE_RUN, KremaBundleRunTask::class.java) { task ->
        // 先打包再起进程，找不到 bundle 时直接报错而不是凭空 launch。
        task.dependsOn(tasks.named(KremaTaskNames.BUNDLE))
        // bundleDir 取自 kremaBundle 刚打出的产物，保证起的就是那一份。
        task.bundleDir.convention(tasks.named(KremaTaskNames.BUNDLE, KremaBundleTask::class.java).flatMap { it.outputDir })
        task.bundleSettings.convention(task.bundleDir.map { resolved.bundleSettings(it.asFile.toPath()).get() })
        task.target.convention(hostTarget())
        task.cleanupService.set(cleanup)
        task.usesService(cleanup)
    }

    tasks.register(KremaTaskNames.SIGNER_GENERATE, KremaSignerGenerateTask::class.java) { task ->
        task.outputDir.convention(kremaOutputDir(ext, buildDir))
        task.privateKey.convention(task.outputDir.file("keys/krema.key"))
    }

    tasks.register(KremaTaskNames.SIGNER_SIGN, KremaSignerSignTask::class.java) { task ->
        task.outputDir.convention(kremaOutputDir(ext, buildDir))
    }
}

private fun hostTarget(): String = "${PlatformDetector.detect().name.lowercase()}-${PlatformDetector.getArch()}"

/** 编译所用 JDK 的 bin 目录：工具跟着编译 JDK 走，不跟 PATH。 */
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

/** 编译 JDK 版本：读 toolchain，没配就是空（launcherFor 自取当前 JVM）。 */
private fun Project.toolchainLanguageVersion(): Provider<JavaLanguageVersion> =
    extensions.getByType(JavaPluginExtension::class.java).toolchain.languageVersion

/** 目标字节码主版本（jdeps --multi-release 用）：取 targetCompatibility。 */
@Throws(GradleException::class)
private fun Project.targetBytecodeVersion(): Provider<String> =
    providers.provider {
        val target = extensions.getByType(JavaPluginExtension::class.java).targetCompatibility
        // jdeps 只接 9+（更低报「选项的参数无效」），模块化本身也是 9 起。
        if (target < JavaVersion.VERSION_1_9) {
            throw GradleException("目标字节码 $target 低于 9，推导不出 JDK 模块；请把 targetCompatibility 提到 9 及以上")
        }
        target.majorVersion
    }
