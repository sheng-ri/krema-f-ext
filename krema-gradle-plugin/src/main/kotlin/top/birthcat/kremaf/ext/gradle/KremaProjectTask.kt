/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.Path

/**
 * 所有 Krema task 的公共输入。
 *
 * Gradle 9.8 下 task action 禁访问 `Project`，所以配置一律在这里声明成 `Property`，
 * 由注册期从扩展取值。
 */
@DisableCachingByDefault(because = "产物是按本机平台落盘的启动器与密钥，不做跨机器复用")
abstract class KremaProjectTask : DefaultTask() {
    /** 项目根目录，也是 `krema.toml` 的解析基准。 */
    @get:Internal
    abstract val projectDir: DirectoryProperty

    /**
     * `krema.toml` 的绝对路径。
     *
     * 暴露成 `String` 而不是 `RegularFileProperty`：`@InputFile` 会在 task action 之前先报
     * 「文件不存在」，那样「缺失硬报错」就退化成 Gradle 的通用校验信息，指不到 `kremaInit`。
     * 走路径字符串则把存在性判断留在 action 里，报错能带上补救指引。
     */
    @get:Input
    abstract val configFilePath: Property<String>

    /** 产物根目录。 */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** 主类，覆盖 `krema.toml` 的 `[build].main_class`。 */
    @get:Input
    @get:Optional
    abstract val mainClass: Property<String>

    /** `krema.toml` 的解析结果，路径不存在时硬失败。 */
    protected fun config(): KremaConfig {
        val toml = Path.of(configFilePath.get())
        if (!Files.isRegularFile(toml)) {
            error("找不到 krema.toml（应为 $toml）—— 先跑 kremaInit，或把 krema.configFile 指对")
        }
        val loaded = KremaConfigLoader().loadOrDefault(toml)
        return loaded.copy(build = loaded.build.copy(mainClass = mainClass.orNull ?: loaded.build.mainClass))
    }

    /** 打包输入，`krema { }` 块的覆盖值已压过 toml。 */
    protected fun bundleSettings(): BundleSettings {
        val config = config()
        val dir = projectDir.get().asFile.toPath()
        return BundleSettings(
            appName = config.packageName,
            outputDir = outputDir.get().asFile.toPath(),
            identifier = config.bundle.identifier ?: config.identifier,
            version = config.version,
            mainClass = config.build.mainClass ?: "${config.identifier}.Main",
            icon = config.bundle.icon?.let(dir::resolve),
            deepLinkSchemes = config.deepLinkSchemes,
        )
    }
}
