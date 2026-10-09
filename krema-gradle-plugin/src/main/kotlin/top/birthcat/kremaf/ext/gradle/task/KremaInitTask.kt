/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.InitSettings
import top.birthcat.kremaf.ext.gradle.TemplateGenerator

/**
 * 生成 Krema 项目骨架。
 *
 * 唯一不要求 `krema.toml` 先存在的 task —— 它的产物之一就是那份 toml。
 */
@DisableCachingByDefault(because = "生成骨架写进用户项目目录，输出不由本 task 独占")
abstract class KremaInitTask : DefaultTask() {
    /** 骨架落点，默认当前项目目录。 */
    @get:Internal
    abstract val projectDir: DirectoryProperty

    /** 骨架根目录，永远等于 [projectDir]；[OutputDirectory] 需要一个独立标注点。 */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** 应用名，同时决定 Java 包名与产物名。 */
    @get:Input
    @get:Optional
    abstract val appName: Property<String>

    @TaskAction
    fun generate() {
        TemplateGenerator().generate(
            projectDir.get().asFile.toPath(),
            InitSettings.defaults(appName.getOrElse("krema-app")),
        )
    }
}
