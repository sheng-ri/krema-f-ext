/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaProjectTask

/**
 * 产出两档共用的构建结果。
 *
 * 编译交回 Gradle：本 task 只等 `classes`（由 Gradle 的 compileJava / processResources 产出），
 * 不再自己开 javac。前端 `dist` 成为声明式输出由 #54 接上。
 */
@DisableCachingByDefault(because = "产物目录的输入输出待 #54 / #55 声明")
abstract class KremaBuildTask : KremaProjectTask() {
    @TaskAction
    fun build() {
        // 仅占位：编译产物来自 Gradle 的 `classes`，打包目录由本 task 持有供后续档位 task 写入。
        outputDir.get().asFile.mkdirs()
    }
}
