/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

/**
 * 跑 `jdeps -v` 打出逐包的依赖明细，供用户自己分析该往 `krema { runtime { modules } }` 里补什么。
 *
 * 与 `jdepsModules` 分成两张task 而不是同一张切开关：jdeps 明说 `-verbose` 与 `--print-module-deps`
 * 互斥，同时给会直接报错退出。
 *
 * 报告文件声明成 [Internal] 而非 `@OutputFile`：它落在 `build/krema/` 下，声明成输出就是第二个
 * 声明该目录的 task。代价是 Gradle 不再替我们建父目录，故写之前自己建。
 */
@DisableCachingByDefault(because = "诊断报告：命中缓存不重跑就什么都看不到")
abstract class KremaJdepsModulesVerboseTask : DefaultTask() {
    /** 应用运行所需的 classpath，模块推导的输入。 */
    @get:Classpath
    abstract val classpathFiles: ConfigurableFileCollection

    /** 报告落点，默认 `build/krema/jdeps-verbose.txt`。 */
    @get:Internal
    abstract val reportFile: RegularFileProperty

    /** jdeps 所在的可执行文件目录，取自 JDK toolchain。 */
    @get:Internal
    abstract val jdkBinDir: Property<String>

    /** jdeps 的 `--multi-release` 版本，取编译产物的目标字节码主版本号。 */
    @get:Input
    abstract val multiRelease: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun printVerbose() {
        val out =
            JdepsRunner(execOperations, Path.of(jdkBinDir.get()), multiRelease.get(), classpathFiles)
                .run(verbose = true)
        // 原样落盘：jdeps 走平台默认字符集，经字符串中转会按 Gradle 的默认编码把非 UTF-8 的报告改写坏。
        val target = reportFile.get().asFile.toPath()
        target.parent?.let(Files::createDirectories)
        Files.write(target, out.bytes)
        if (out.exitValue != 0) {
            // 不当失败：-v 在部分 jar 组合下会一边非零退出一边照常吐出可用的报告，
            // 而这份报告正是用户跑这张 task 想看的东西。
            logger.warn("jdeps -v 退出码 ${out.exitValue}，明细已写入 ${target.toAbsolutePath()}")
        }
        logger.lifecycle(out.text)
    }
}
