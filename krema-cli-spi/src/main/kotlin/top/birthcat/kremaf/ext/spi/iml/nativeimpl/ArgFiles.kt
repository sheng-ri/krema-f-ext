/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.nativeimpl

/**
 * 命令行参数文件（`@argfile`）的内容排版。
 *
 * Windows 的 `CreateProcess` 上限约 32767 字符，classpath 一长就 `-cp` 都递不过去；
 * Gradle 侧更是走 `ExecOperations`，连自己切段重试的余地都没有。参数文件是 javax0/UPX
 * 那类工具的老办法，也是 java launcher 原生支持的一档。
 *
 * 排版规则对齐 java launcher 的 `JLI_Launch`（GraalVM 的 `native-image` 是同一个 launcher）：
 * 空白分隔 token、成对双引号保住含空白的 token、`#` 不是注释、空行忽略、`\r\n` 与 `\n` 都认。
 * 编排层只递路径与 flag，不会递裸引号，所以这里不实现转义：引号一旦出现就丢掉，
 * 免得留一个孤引号把后面的 token 全吃进同一个字符串。
 */
internal object ArgFiles {
    /** 参数文件后缀。java launcher 认任意名字，带个后缀只为文件名可读。 */
    const val SUFFIX = ".args"

    /**
     * 把一组命令行参数排版成参数文件内容。
     *
     * 一行一个 token：含空白等危险字符的 token 要引起来，否则 launcher 会把一个路径劈成两个参数。
     */
    fun render(args: List<String>): String = args.joinToString("\n", postfix = "\n") { quote(it) }

    /**
     * 给 token 加引号。
     *
     * 需要引号兜住的字符：[needsQuoting]。其余字符原样留着，
     * Windows 路径里的 `\` 只要不在行尾就不必动。
     */
    private fun quote(token: String): String =
        if (token.none(::needsQuoting)) {
            token
        } else {
            '"' + token.filterNot { it == '"' } + '"'
        }

    private fun needsQuoting(c: Char): Boolean = c == ' ' || c == '\t' || c == '\\' || c == '"'
}
