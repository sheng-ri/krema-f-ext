/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.tool.JdepsRunner
import java.nio.file.Path
import javax.inject.Inject

/**
 * 打出 jdeps 推导出的运行时模块清单，让用户在裁镜像之前先看清依赖。
 *
 * 直接继承 [DefaultTask] 而非 Krema 的基类：它既不读 `krema.toml` 也不产出文件，挂上
 * `KremaConfigTask` 会拖进一堆无关的 toml 输入，挂上 `KremaProjectTask` 还会把 `build/krema`
 * 声明成输出目录，与 kremaBuild 等 task 撞成重叠输出。
 */
@DisableCachingByDefault(because = "只打印推导结果，不产出文件")
abstract class KremaJdepsModulesTask : DefaultTask() {
    /** 应用运行所需的 classpath，模块推导的输入。 */
    @get:Classpath
    abstract val classpathFiles: ConfigurableFileCollection

    /** jdeps 所在的可执行文件目录，取自 JDK toolchain。 */
    @get:Internal
    abstract val jdkBinDir: Property<String>

    /** jdeps 的 `--multi-release` 版本，取编译产物的目标字节码主版本号。 */
    @get:Input
    abstract val multiRelease: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    @Throws(GradleException::class)
    fun printModules() {
        val out =
            JdepsRunner(execOperations, Path.of(jdkBinDir.get()), multiRelease.get(), classpathFiles)
                .run(verbose = false)
        val modules = out.firstLine()
        if (out.exitValue != 0 || modules.isEmpty()) {
            throw GradleException("推导运行时模块失败：\n${out.text}")
        }
        // 打到 lifecycle 而不是 println：TestKit 抓的是 Gradle 的日志体系。
        logger.lifecycle("运行时模块：$modules")
    }
}
