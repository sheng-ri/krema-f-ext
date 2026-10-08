/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import top.birthcat.kremaf.ext.gradle.task.KremaBuildNativeTask
import top.birthcat.kremaf.ext.gradle.task.KremaBuildTask
import top.birthcat.kremaf.ext.gradle.task.KremaBundleTask
import top.birthcat.kremaf.ext.gradle.task.KremaDevTask
import top.birthcat.kremaf.ext.gradle.task.KremaInitTask
import top.birthcat.kremaf.ext.gradle.task.KremaSignerGenerateTask
import top.birthcat.kremaf.ext.gradle.task.KremaSignerSignTask

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

    /** 全部 task 名，声明顺序即 `gradlew tasks` 里的出现顺序。 */
    val ALL = listOf(INIT, BUILD, BUILD_NATIVE, BUNDLE, DEV, SIGNER_GENERATE, SIGNER_SIGN)
}

internal fun Project.registerKremaTasks() {
    val ext = extensions.getByType(KremaExtension::class.java)
    val frontend = ext.frontend

    tasks.register(KremaTaskNames.INIT, KremaInitTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "生成 Krema 项目骨架（krema.toml + 主类）"
        task.projectDir.convention(layout.projectDirectory)
        task.outputDir.convention(layout.projectDirectory)
    }

    tasks.register(KremaTaskNames.BUILD, KremaBuildTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "编译 Java 源码并跑一次前端生产构建"
        task.configureProject(ext, frontend)
    }

    tasks.register(KremaTaskNames.BUILD_NATIVE, KremaBuildNativeTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "在 kremaBuild 之上打 native-image（工具链见 #26）"
        task.configureProject(ext, frontend)
    }

    tasks.register(KremaTaskNames.BUNDLE, KremaBundleTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "为当前平台生成可分发的安装包"
        task.configureProject(ext, frontend)
    }

    tasks.register(KremaTaskNames.DEV, KremaDevTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "起前端 dev server 与应用窗口（长驻，不进 CI 门禁）"
        task.configureProject(ext, frontend)
    }

    tasks.register(KremaTaskNames.SIGNER_GENERATE, KremaSignerGenerateTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "生成一对签名密钥"
        task.configureProject(ext, frontend)
        task.privateKey.convention(task.outputDir.file("keys/krema.key"))
    }

    tasks.register(KremaTaskNames.SIGNER_SIGN, KremaSignerSignTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "用私钥给产物签名"
        task.configureProject(ext, frontend)
    }
}

/**
 * 公共输入的默认值。
 *
 * 默认值唯一住所是 `KremaRegistry.Build()` 与 `krema.toml`；这里只把「从哪取」接上，不给业务默认值。
 */
internal fun KremaProjectTask.configureProject(
    ext: KremaExtension,
    frontend: KremaFrontendExtension = ext.frontend,
) {
    projectDir.convention(project.layout.projectDirectory)
    configFilePath.convention(
        ext.configFile.orElse(project.layout.projectDirectory.file("krema.toml")).map { it.asFile.absolutePath },
    )
    outputDir.convention(ext.outputDir.orElse(project.layout.buildDirectory.dir("krema")))
    // 应用自己的输出不属于自己的 runtimeClasspath，得单独接上。
    // java 插件在就取 main source set 的输出；不在（裸 Kotlin）就退到 Kotlin 约定目录。
    if (project.plugins.hasPlugin("java")) {
        val main = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
        classOutputDir.convention(main.java.destinationDirectory)
        runtimeClasspath.from(main.output)
    } else {
        classOutputDir.convention(project.layout.buildDirectory.dir("classes/kotlin/main"))
    }
    project.configurations.findByName("runtimeClasspath")?.let(runtimeClasspath::from)
    frontendBuildCommand.convention(frontend.buildCommand)
    frontendDevCommand.convention(frontend.devCommand)
    frontendDevUrl.convention(frontend.devUrl)
    mainClass.convention(ext.mainClass)
}
