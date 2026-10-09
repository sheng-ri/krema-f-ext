/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.Project
import top.birthcat.kremaf.ext.gradle.task.KremaBuildNativeTask
import top.birthcat.kremaf.ext.gradle.task.KremaBuildTask
import top.birthcat.kremaf.ext.gradle.task.KremaBundleTask
import top.birthcat.kremaf.ext.gradle.task.KremaDevTask
import top.birthcat.kremaf.ext.gradle.task.KremaFrontendBuildTask
import top.birthcat.kremaf.ext.gradle.task.KremaInitTask
import top.birthcat.kremaf.ext.gradle.task.KremaSignerGenerateTask
import top.birthcat.kremaf.ext.gradle.task.KremaSignerSignTask
import java.nio.file.Files
import java.nio.file.Path

/** `krema` 分组下 7 张 task 的名字，TestKit 与 README 都从这里取。 */
object KremaTaskNames {
    const val GROUP = "krema"
    const val INIT = "kremaInit"
    const val BUILD = "kremaBuild"
    const val BUILD_NATIVE = "kremaBuildNative"
    const val BUNDLE = "kremaBundle"
    const val DEV = "kremaDev"
    const val SIGNER_GENERATE = "kremaSignerGenerate"
    const val SIGNER_SIGN = "kremaSignerSign"

    /** 内部支撑 task：跑前端生产构建，不是用户面向的 task，不进 `krema` 分组也不进 `ALL`。 */
    const val FRONTEND_BUILD = "kremaFrontendBuild"

    /** 全部 task 名，声明顺序即 `gradlew tasks` 里的出现顺序。 */
    val ALL = listOf(INIT, BUILD, BUILD_NATIVE, BUNDLE, DEV, SIGNER_GENERATE, SIGNER_SIGN)
}

internal fun Project.registerKremaTasks() {
    val ext = extensions.getByType(KremaExtension::class.java)

    tasks.register(KremaTaskNames.INIT, KremaInitTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "生成 Krema 项目骨架（krema.toml + 主类）"
        task.projectDir.convention(layout.projectDirectory)
        task.outputDir.convention(layout.projectDirectory)
    }

    tasks.register(KremaTaskNames.FRONTEND_BUILD, KremaFrontendBuildTask::class.java) { task ->
        // 内部支撑 task：不进 krema 分组，也不出现在 KremaTaskNames.ALL（用户面向的 7 张）。
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

    tasks.register(KremaTaskNames.BUILD_NATIVE, KremaBuildNativeTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "在 kremaBuild 之上打 native-image（工具链见 #26）"
        task.configureProject(ext)
    }

    tasks.register(KremaTaskNames.BUNDLE, KremaBundleTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "生成可分发的平台安装包"
        task.configureProject(ext)
        task.target.convention(currentTarget())
    }

    tasks.register(KremaTaskNames.DEV, KremaDevTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "起前端 dev server 与应用窗口（长驻，不进 CI 门禁）"
        task.configureProject(ext)
    }

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

/** 公共输入的默认值：扩展里给了就用扩展的，没给就取约定位置。 */
internal fun KremaProjectTask.configureProject(ext: KremaExtension) {
    projectDir.convention(project.layout.projectDirectory)
    configFilePath.convention(
        ext.configFile.orElse(project.layout.projectDirectory.file("krema.toml")).map { it.asFile.absolutePath },
    )
    configFileSetExplicitly.convention(ext.configFile.isPresent)
    configFilePresent.convention(project.provider { Files.exists(Path.of(configFilePath.get())) })
    outputDir.convention(ext.outputDir.orElse(project.layout.buildDirectory.dir("krema")))
    mainClass.convention(ext.mainClass)
}

private fun currentTarget(): String {
    val platform = PlatformDetector.detect().name.lowercase()
    val arch = PlatformDetector.getArch()
    return "$platform-$arch"
}
