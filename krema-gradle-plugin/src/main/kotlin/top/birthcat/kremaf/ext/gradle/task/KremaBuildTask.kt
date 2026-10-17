/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
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
abstract class KremaBuildTask : Sync() {
    init {
        group = KremaTaskNames.GROUP
        description = "Assemble backend and frontend: build the app jar and a separate assets jar into lib/"
        into(outputDir.dir(LIB_DIR))
        from(appJar)
        from(assetsJar)
        from(dependencyJars)
    }

    /** 产物根目录，bundle task 据此算 `lib/` 路径。 */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** 专用 app jar（由 kremaAppJar 产出）：classes + resources 收全，manifest 带 Main-Class。 */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val appJar: RegularFileProperty

    /** 运行期依赖 jar（已剔除工程自身 jar）。 */
    @get:Classpath
    abstract val dependencyJars: ConfigurableFileCollection

    /** 前端 dist 打的 assets jar（由 kremaAssetsJar 产出），内容落在 `assets/` 下、Krema 从 classpath 读。 */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val assetsJar: RegularFileProperty

    private companion object {
        const val LIB_DIR = "lib"
    }
}
