/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.nio.file.Path

/**
 * 外部命令执行口。
 *
 * 编排层只表达「跑哪条命令、在哪个目录、要什么环境变量」，进程怎么起（Gradle 里是 `ExecOperations`）由实现决定。
 */
interface CommandRunner {
    /**
     * 跑一条命令并等它结束。
     *
     * @param command 命令行参数，第一项是可执行文件名
     * @param workingDir 工作目录
     * @param env 追加的环境变量
     * @return 退出码
     */
    fun exec(
        command: List<String>,
        workingDir: Path,
        env: Map<String, String> = emptyMap(),
    ): Int

    /**
     * 起一个长驻进程。
     *
     * @param command 命令行参数
     * @param workingDir 工作目录
     * @param env 追加的环境变量
     * @return 子进程句柄
     */
    fun spawn(
        command: List<String>,
        workingDir: Path,
        env: Map<String, String> = emptyMap(),
    ): Process

    /**
     * 查询可执行文件路径。
     *
     * @param name 可执行文件名，不带扩展名
     * @return 绝对路径，找不到时返回 null
     */
    fun which(name: String): Path?
}
