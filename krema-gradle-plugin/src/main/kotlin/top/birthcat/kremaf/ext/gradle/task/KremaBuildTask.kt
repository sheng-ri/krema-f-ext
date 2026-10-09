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
 * 编译交回 Gradle 的 `compileJava` 由 #53 接上，前端 `dist` 成为声明式输出由 #54 接上。
 */
@DisableCachingByDefault(because = "产物目录的输入输出待 #53 / #54 声明")
abstract class KremaBuildTask : KremaProjectTask() {
    @TaskAction
    fun build() {
        val app = config().packageName
        logger.warn("kremaBuild：$app 的编译交回 Gradle 见 #53，前端 dist 见 #54")
    }
}
