/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSet
import org.gradle.work.DisableCachingByDefault
import java.io.File
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

    /**
     * 用户是否在 `krema { }` 里显式给了 `configFile`。
     *
     * 区分「显式指定」与「走默认路径」：只有显式指定的文件不存在才硬报错，默认路径缺失只是无值。
     */
    @get:Input
    abstract val configFileSetExplicitly: Property<Boolean>

    /**
     * 解析出来的 `krema.toml` 当前是否存在。
     *
     * 路径字符串本身不变，删/建文件不会让 task 重新执行，所以用这个布尔值作为 input 参与 up-to-date。
     */
    @get:Input
    abstract val configFilePresent: Property<Boolean>

    /** `krema.toml` 的解析结果。默认路径缺失即无值、不报错；显式指定却缺失才硬失败指到路径。 */
    protected fun config(): KremaConfig {
        val toml = Path.of(configFilePath.get())
        val loaded =
            if (configFilePresent.get()) {
                KremaConfigLoader().loadOrDefault(toml)
            } else if (configFileSetExplicitly.get()) {
                error("krema.configFile 指向的 krema.toml 不存在：$toml")
            } else {
                KremaConfig()
            }
        val explicitMain = mainClass.orNull
        return loaded.copy(build = loaded.build.copy(mainClass = explicitMain ?: loaded.build.mainClass))
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

/** 主 source set 的编译产物与运行期 classpath，供 #55 把应用进程打进 bundle。 */
data class KremaApplicationClasspath(
    val classesDirs: FileCollection,
    val resourcesDir: File?,
    val runtimeClasspath: Configuration,
)

/**
 * 应用进程运行所需的 classpath 来源。
 *
 * 插件不假设编译方言：Java 与 Kotlin 的产出都落在 `sourceSets["main"]` 的输出上，
 * 因此直接取 Gradle 标准位置，不认插件自造的源码目录。
 */
fun Project.kremaApplicationClasspath(): KremaApplicationClasspath {
    val main = extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main")
    return KremaApplicationClasspath(
        classesDirs = main.output.classesDirs,
        resourcesDir = main.output.resourcesDir,
        runtimeClasspath = configurations.getByName("runtimeClasspath"),
    )
}
