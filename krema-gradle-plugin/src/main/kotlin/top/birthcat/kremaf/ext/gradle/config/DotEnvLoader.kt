/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import java.nio.file.Files
import java.nio.file.Path

/**
 * `.env` 读取。
 *
 * 自研：解析规则对齐上游 `krema-cli` 的 `DotEnvLoader`（该模块不发制品）——
 * 跳过空行与 `#` 注释，只在第一个 `=` 处切分，剥掉成对的引号。不可读的文件静默跳过。
 */
class DotEnvLoader {
    /**
     * 读取环境变量。
     *
     * @param projectDir `.env` 所在目录
     * @return 解析出的键值
     */
    fun load(projectDir: Path): Map<String, String> {
        val vars = LinkedHashMap<String, String>()
        val envPath = projectDir.resolve(".env")
        if (Files.isRegularFile(envPath)) parseInto(Files.readString(envPath), vars)
        return vars
    }

    private fun parseInto(
        content: String,
        vars: MutableMap<String, String>,
    ) {
        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val eq = trimmed.indexOf('=')
            if (eq < 0) continue
            vars[trimmed.take(eq).trim()] = unquote(trimmed.substring(eq + 1).trim())
        }
    }

    private fun unquote(value: String): String {
        if (value.length < 2) return value
        val first = value.first()
        return if ((first == '"' || first == '\'') && value.last() == first) value.substring(1, value.length - 1) else value
    }
}
