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
import top.birthcat.kremaf.ext.gradle.config.BundleSettings
import top.birthcat.kremaf.ext.gradle.config.KremaConfig
import top.birthcat.kremaf.ext.gradle.config.KremaConfigLoader
import top.birthcat.kremaf.ext.gradle.config.KremaExtension
import top.birthcat.kremaf.ext.gradle.config.appJarName
import top.birthcat.kremaf.ext.gradle.config.assetsJarName
import top.birthcat.kremaf.ext.gradle.config.platformOutputDirName
import top.birthcat.kremaf.ext.gradle.config.resolveMainClass
import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.PlatformDetector
import top.birthcat.kremaf.ext.gradle.domain.appImageDirName
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
import top.birthcat.kremaf.ext.gradle.task.configureProject
import top.birthcat.kremaf.ext.gradle.task.kremaApplicationClasspath

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
        // out_dir 是前端工具链自己的落点，插件只能按它读：读错地方 prod 的 app jar 就空 assets。
        task.description = "Run the frontend production build (npm); output dir from krema.toml out_dir, declared as cacheable"
        task.configureProject(ext)
        // 前端命令两层：toml 是底，krema.frontend { } 显式写的压过。
        val tomlFrontend = providers.provider { kremaConfig(ext).frontend }
        task.buildCommand.convention(tomlFrontend.map { it.buildCommand })
        ext.frontend.buildCommand.orNull
            ?.let { task.buildCommand.convention(it) }
        task.installCommand.convention(tomlFrontend.map { it.installCommand })
        ext.frontend.installCommand.orNull
            ?.let { task.installCommand.convention(it) }
        conventionFrontendDir(ext, task.frontendDir)
        val tomlOutDir = providers.provider { kremaConfig(ext).build.outDir }
        task.distDir.convention(task.frontendDir.flatMap { front -> tomlOutDir.map { front.dir(it) } })
        task.frontendInputs.from(
            listOf("package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml")
                .map { task.frontendDir.dir(it) },
        )
    }

    // classes + 运行期 classpath：kremaBuild、运行时镜像与依赖分析共用。
    val classpath = kremaApplicationClasspath()

    // 两个 jar 必须顶层注册：register 闭包懒执行，跑到时 task set 已冻结，register 直接报错。
    val settings = providers.provider { kremaBuildSettings(ext) }
    // classes 显式依赖：jar 的 from 是输出目录位置，不声明看不到产出它的 processResources。
    val classesTask = tasks.named("classes")
    val appJarTask =
        tasks.register(KremaTaskNames.APP_JAR, Jar::class.java) { jar ->
            jar.dependsOn(classesTask)
            jar.from(classpath.classesDirs)
            classpath.resourcesDir?.let { jar.from(it) }
            jar.manifest { it.attributes(mapOf("Main-Class" to settings.map { it.mainClass }.get())) }
            jar.archiveFileName.set(settings.map { appJarName(it) })
        }

    val assetsJarTask =
        tasks.register(KremaTaskNames.ASSETS_JAR, Jar::class.java) { jar ->
            jar.from(
                tasks
                    .named(KremaTaskNames.FRONTEND_BUILD, KremaFrontendBuildTask::class.java)
                    .map { it.distDir },
            ) { it.into(ASSETS_DIR) }
            jar.archiveFileName.set(settings.map { assetsJarName(it) })
        }

    tasks.register(KremaTaskNames.BUILD, KremaBuildTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "Assemble backend and frontend: build the app jar and a separate assets jar into lib/"
        task.configureProject(ext)
        task.appJar.set(appJarTask.flatMap { it.archiveFile })
        task.assetsJar.set(assetsJarTask.flatMap { it.archiveFile })
        // 仅收集依赖 jar
        task.dependencyJars.from(classpath.runtimeClasspath)
    }

    tasks.register(KremaTaskNames.RUNTIME_IMAGE, KremaRuntimeImageTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "Derive modules via jdeps and trim a minimal runtime image with jlink (build/krema/runtime)"
        task.configureProject(ext)
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
        task.group = KremaTaskNames.GROUP
        task.description = "Print the runtime module list derived from jdeps, without trimming an image"
        // 不依赖 classes 的话 classesDirs 不存在，推导会漏掉应用自己的代码。
        task.dependsOn(tasks.named("classes"))
        task.jdkBinDir.set(jdkBinDir())
        task.multiRelease.set(targetBytecodeVersion())
        task.classpathFiles.from(classpath.classesDirs)
        task.classpathFiles.from(classpath.runtimeClasspath)
    }

    tasks.register(KremaTaskNames.JDEPS_MODULES_VERBOSE, KremaJdepsModulesVerboseTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "Print per-package jdeps -v dependency details and write a report to build/krema/jdeps-verbose.txt"
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
        task.group = KremaTaskNames.GROUP
        task.description = "Build a runnable, uncompressed app-image (build/krema/bundle) with the jlink-trimmed runtime"
        task.configureProject(ext)
        task.dependsOn(tasks.named(KremaTaskNames.BUILD))
        task.dependsOn(tasks.named(KremaTaskNames.RUNTIME_IMAGE))
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
                    task.group = KremaTaskNames.GROUP
                    task.description = "Build the ${format.id} platform package from the app-image (build/krema/platform/${format.id})"
                    task.configureProject(ext)
                    task.dependsOn(tasks.named(KremaTaskNames.BUNDLE))
                    kremaConfig(ext)
                    task.format.set(format)
                    task.outputDir.convention(
                        ext.outputDir.orElse(layout.buildDirectory.dir("krema")).map { it.dir(platformOutputDirName(format.id)) },
                    )
                    // app-image 目录 = kremaBundle 产物下按平台命名的子目录。
                    // 子目录名配置期算成 String：闭包里调 kremaConfig(ext) 会捕获
                    // Project，配置缓存序列化时直接丢缓存。
                    // appImageDir 以 tasks.named(BUNDLE) 的 provider 为源，
                    // 不捕获 Task 对象（同样不可序列化）。
                    val appImageDirName =
                        Platform.of(System.getProperty("os.name")).appImageDirName(kremaConfig(ext).packageName)
                    task.appImageDir.convention(
                        tasks
                            .named(KremaTaskNames.BUNDLE, KremaBundleTask::class.java)
                            .flatMap { it.outputDir }
                            .map { it.dir(appImageDirName) },
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
        task.configureProjectDevRun(ext)
        // 干净仓库也要先编译：否则 classesDirs 还空着，起的进程照样找不到主类。
        task.dependsOn(tasks.named("classes"))
        // 取编译产物 + 运行期依赖：光 runtimeClasspath 不含项目自己的类，
        // 起的进程会 ClassNotFound 找不到主类。
        task.classpath = classpath.classesDirs + classpath.runtimeClasspath
        // 主类配置期算成 String 设进 convention：provider 闭包里调
        // task.resolvedMainClass() 会捕获 task 对象，配置缓存不可序列化。
        task.mainClass.convention(task.resolvedMainClass())
        task.doFirst { task.environment("KREMA_DEV_URL", task.resolvedDevUrl()) }
        // 不依赖 kremaBuild/FrontendBuild：前端由使用方自己的 dev server 提供。
    }

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

// Jar task 取不到 bundleSettings()，按同口径推 jar 名与主类。
private fun Project.kremaBuildSettings(ext: KremaExtension): BundleSettings {
    val config = kremaConfig(ext)
    return BundleSettings(appName = config.packageName, mainClass = resolveMainClass(config, ext.mainClass.orNull))
}

/** 注册期就要用 krema.toml，故配置期直读；显式路径优先，否则取根下约定。 */
private fun Project.kremaConfig(ext: KremaExtension): KremaConfig {
    val toml =
        ext.configFile
            .orElse(layout.projectDirectory.file("krema.toml"))
            .get()
            .asFile
            .toPath()
    return KremaConfigLoader().loadOrDefault(toml)
}

/** 前端目录两层取值：扩展 dir 覆盖 toml，都没写即项目根。 */
private fun Project.conventionFrontendDir(
    ext: KremaExtension,
    target: org.gradle.api.file.DirectoryProperty,
) {
    // 键缺失时兜成 "."（项目根本机），不在 provider 里判 null：
    // provider 泛型不带空性标注，map 里判 null 会被当恒假。
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
        // 先打包再起进程，找不到 bundle 时直接报错而不是凭空 launch。
        task.dependsOn(tasks.named(bundleTask))
        // bundleDir 取自 kremaBundle 刚打出的产物，保证起的就是那一份。
        task.bundleDir.convention(bundleDir)
        task.target.convention(hostTarget())
        task.cleanupService.set(cleanup)
        task.usesService(cleanup)
    }
}

private fun hostTarget(): String = "${PlatformDetector.detect().name.lowercase()}-${PlatformDetector.getArch()}"

private fun KremaDevRunTask.configureProjectDevRun(ext: KremaExtension) {
    configFilePath.convention(
        ext.configFile.orElse(project.layout.projectDirectory.file("krema.toml")).map { it.asFile.absolutePath },
    )
    configFileSetExplicitly.convention(ext.configFile.isPresent)
    mainClassOverride.convention(ext.mainClass)
    devUrlOverride.convention(ext.devUrl)
}

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
