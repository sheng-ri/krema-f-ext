/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.nio.file.Path

/**
 * 现压一个可执行主 jar。
 *
 * 应用自己的编译输出不属于自己的 `runtimeClasspath`，所以 classpath 上没有同名主 jar 时，
 * 得有人把它压出来 —— 打包（jpackage 的 `--main-jar`）与启动都等这个文件。
 */
class ExecutableJar(
    private val fileSystem: FileSystem,
    private val commandRunner: CommandRunner,
) {
    /**
     * 把编译输出压成带主类 manifest 的 jar。
     *
     * @param output 目标 jar 路径，父目录不存在时自动创建
     * @param classOutputDir 编译输出目录，压成 jar 的根
     * @param mainClass manifest 里的主类
     */
    fun build(
        output: Path,
        classOutputDir: Path,
        mainClass: String,
    ) {
        check(fileSystem.list(classOutputDir).isNotEmpty()) {
            "编译输出目录不存在或是空的：$classOutputDir —— 先跑一次编译，或把 krema.toml 的 [build].java_source_dir 指对"
        }
        val jar = commandRunner.which("jar")?.toString() ?: "jar"
        val exit =
            commandRunner.exec(
                listOf(
                    jar,
                    "--create",
                    "--file",
                    output.toString(),
                    "--main-class",
                    mainClass,
                    "-C",
                    classOutputDir.toString(),
                    ".",
                ),
                classOutputDir,
            )
        check(exit == 0) { "jar 打包失败，退出码 $exit" }
    }
}
