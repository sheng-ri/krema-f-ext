/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import top.birthcat.kremaf.ext.gradle.KremaTaskNames

/**
 * 整个应用的聚合层：用 Gradle 的 [Sync] 把 app jar、assets jar 与运行期依赖 jar 收进同一个 `lib/`。
 *
 * [Sync] 自带删旧（dest 里不在源中的文件会被清掉），等价于原来「删 lib 再重建」，且省掉手写 `Files.copy`。
 * bundle task 直接拿 `lib/` 去 jpackage。前端是「整个应用」的资源，故与后端一起在此聚合，
 * 而不是塞进 bundle 的打包逻辑里——bundle 只管最终平台分发。
 */
@CacheableTask
abstract class KremaBuildTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJars: ConfigurableFileCollection
}
