/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import top.birthcat.kremaf.ext.gradle.task.KremaBuildTask
import top.birthcat.kremaf.ext.gradle.task.KremaBundleRunTask
import top.birthcat.kremaf.ext.gradle.task.KremaBundleTask
import top.birthcat.kremaf.ext.gradle.task.KremaFrontendBuildTask
import top.birthcat.kremaf.ext.gradle.task.KremaInitTask
import top.birthcat.kremaf.ext.gradle.task.KremaSignerGenerateTask
import top.birthcat.kremaf.ext.gradle.task.KremaSignerSignTask

internal fun Project.registerKremaTasks() {
    val ext = extensions.getByType(KremaExtension::class.java)

    val cleanup =
        gradle.sharedServices.registerIfAbsent("kremaProcessCleanup", KremaProcessCleanupService::class.java) {}

    tasks.register(KremaTaskNames.INIT, KremaInitTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "生成 Krema 项目骨架（krema.toml + 主类）"
        task.projectDir.convention(layout.projectDirectory)
        task.outputDir.convention(layout.projectDirectory)
    }

    tasks.register(KremaTaskNames.FRONTEND_BUILD, KremaFrontendBuildTask::class.java) { task ->
        // 内部支撑 task：不进 krema 分组，也不出现在 KremaTaskNames.ALL（用户面向的 8 张）。
        task.group = null
        task.description = "跑前端生产构建（npm 命令），产物为 build/krema/dist 并声明成可缓存输出"
        task.configureProject(ext)
        task.buildCommand.convention(ext.frontend.buildCommand)
        task.distDir.convention(task.outputDir.dir("dist"))
        // 前端输入在项目根解析；哪些锁文件存在就收集哪些，缺失的候选自然不在集合里。
        val projectDir = project.layout.projectDirectory
        task.frontendInputs.from(
            listOf("package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml")
                .map { projectDir.file(it) },
        )
    }

    tasks.register(KremaTaskNames.BUILD, KremaBuildTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "让 Gradle 编译主 source set，并把前端 dist 收进 build/krema"
        task.configureProject(ext)
        // 编译交回 Gradle：kremaBuild 跑在 compileJava / processResources 之后。
        task.dependsOn(tasks.named("classes"))
        // 前端 dist 是 kremaBuild 的共用前置：声明成依赖后它才成为可缓存、UP-TO-DATE 的产出。
        task.dependsOn(tasks.named(KremaTaskNames.FRONTEND_BUILD))
    }

    // dev / prod 两张打包 task：都先跑 kremaBuild（编译 + 前端），dev 额外收最小形态、不打包 dist。
    val classpath = kremaApplicationClasspath()
    registerBundle(KremaTaskNames.BUNDLE_DEV, dev = true, ext, classpath)
    registerBundle(KremaTaskNames.BUNDLE_PROD, dev = false, ext, classpath)

    registerRun(KremaTaskNames.BUNDLE_RUN_DEV, KremaTaskNames.BUNDLE_DEV, dev = true, ext, cleanup)
    registerRun(KremaTaskNames.BUNDLE_RUN_PROD, KremaTaskNames.BUNDLE_PROD, dev = false, ext, cleanup)

    tasks.register(KremaTaskNames.SIGNER_GENERATE, KremaSignerGenerateTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "生成一对签名密钥"
        task.configureProject(ext)
        task.privateKey.convention(task.outputDir.file("keys/krema.key"))
    }

    tasks.register(KremaTaskNames.SIGNER_SIGN, KremaSignerSignTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "用私钥给产物签名"
        task.configureProject(ext)
    }
}

private fun Project.registerBundle(
    name: String,
    dev: Boolean,
    ext: KremaExtension,
    classpath: KremaApplicationClasspath,
) {
    tasks.register(name, KremaBundleTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description =
            if (dev) {
                "生成 dev 档 bundle（最小形态，前端走 devUrl）"
            } else {
                "生成 prod 档 bundle（完整可分发形态，含前端 dist）"
            }
        task.configureProject(ext)
        // 两档各占独立输出目录，互不抢同一个产物根。
        task.outputDir.convention(
            ext.outputDir.orElse(layout.buildDirectory.dir("krema")).map { it.dir(if (dev) "dev" else "prod") },
        )
        task.target.convention(currentTarget())
        task.dev.convention(dev)
        // 应用 classpath 直接取 Gradle 的 runtimeClasspath（已含主 source set 产出与运行期依赖）。
        task.classpathFiles.from(configurations.named("runtimeClasspath"))
        if (dev) {
            task.distFiles.setFrom(emptyList<Any>())
        } else {
            task.distFiles.from(
                tasks.named(KremaTaskNames.FRONTEND_BUILD, KremaFrontendBuildTask::class.java).map { it.distDir },
            )
        }
        // 打包建立在编译 + 前端产出之上。
        task.dependsOn(tasks.named(KremaTaskNames.BUILD))
    }
}

private fun Project.registerRun(
    name: String,
    bundleTask: String,
    dev: Boolean,
    ext: KremaExtension,
    cleanup: Provider<KremaProcessCleanupService>,
) {
    tasks.register(name, KremaBundleRunTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description =
            if (dev) {
                "起前端 dev server 并跑 dev 档应用（长驻，不进 CI 门禁）"
            } else {
                "跑 prod 档应用（长驻，不进 CI 门禁）"
            }
        task.configureProject(ext)
        task.bundleDir.convention(
            ext.outputDir.orElse(layout.buildDirectory.dir("krema")).map { it.dir(if (dev) "dev" else "prod") },
        )
        task.target.convention(currentTarget())
        task.dev.convention(dev)
        task.devCommand.convention(ext.frontend.devCommand)
        task.cleanupService.set(cleanup)
        task.usesService(cleanup)
        // 先打包再起进程，找不到 bundle 时直接报错而不是凭空 launch。
        task.dependsOn(tasks.named(bundleTask))
    }
}

private fun currentTarget(): String {
    val platform = PlatformDetector.detect().name.lowercase()
    val arch = PlatformDetector.getArch()
    return "$platform-$arch"
}
