/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.spi.iml.delegate.KremaConfigLoader
import top.birthcat.kremaf.ext.spi.port.Build
import top.birthcat.kremaf.ext.spi.port.BundleSettings
import top.birthcat.kremaf.ext.spi.port.Frontend
import top.birthcat.kremaf.ext.spi.port.JavaProject
import top.birthcat.kremaf.ext.spi.port.KremaConfig
import top.birthcat.kremaf.ext.spi.port.KremaProject
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import top.birthcat.kremaf.ext.spi.port.Classpath as ClasspathPort

/**
 * 所有 Krema task 的公共输入。
 *
 * Gradle 9.8 下 task action 禁访问 `Project`，所以配置一律在这里声明成 `Property`，
 * 由注册期从扩展取值。扩展面只开 `[build]` 一道缝：其余 section 归 `krema.toml`。
 */
@DisableCachingByDefault(because = "编排层调外部工具（javac/npm/jpackage），产物不可跨机器复用")
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

    /** `[build]` 缝：前端构建命令，覆盖 toml。 */
    @get:Input
    @get:Optional
    abstract val frontendBuildCommand: Property<String>

    /** `[build]` 缝：前端 dev 命令，覆盖 toml。 */
    @get:Input
    @get:Optional
    abstract val frontendDevCommand: Property<String>

    /** `[build]` 缝：前端 dev server 地址，覆盖 toml。 */
    @get:Input
    @get:Optional
    abstract val frontendDevUrl: Property<String>

    /** 运行期 profile，透传给 `KremaConfigLoader`。 */
    @get:Input
    @get:Optional
    abstract val profile: Property<String>

    /** 主类，覆盖 `krema.toml` 的 `[build].main_class`。 */
    @get:Input
    @get:Optional
    abstract val mainClass: Property<String>

    /** 运行时 classpath，由 Gradle 侧从 `Configuration` 取。 */
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    /** 本项目编译输出目录，前端资源会先被 Gradle 拷进来。 */
    @get:Internal
    abstract val classOutputDir: DirectoryProperty

    /** 子进程执行口，由 Gradle 注入。 */
    @get:Inject
    abstract val execOperations: ExecOperations

    /** 装配好的 port 集合。 */
    protected fun ports(): KremaPorts =
        KremaPorts(
            commandRunner = GradleCommandRunner(execOperations),
            classpathResolver = GradleClasspathResolver(runtimeClasspath, classOutputDir.get().asFile.toPath()),
        )

    protected fun projectPath(): Path = projectDir.get().asFile.toPath()

    /** `krema.toml` 路径，未配置时取项目根下同名文件。 */
    protected fun configPath(): Path = Path.of(configFilePath.get())

    /**
     * 解析后的项目输入，编排层的统一入口。
     *
     * @param classpath 要打包的 runtime classpath，jpackage 的 `--input` 直接吃它
     */
    protected fun kremaProject(classpath: ClasspathPort? = null): KremaProject {
        val dir = projectPath()
        val config = loadConfig(configPath())
        val build = config.build
        return KremaProject(
            dir = dir,
            config = config,
            java =
                JavaProject(
                    mainClass = mainClass.orNull ?: build.mainClass,
                    sourceDir = dir.resolve(build.javaSourceDir),
                ),
            frontend = frontend(config.frontend),
            bundle =
                BundleSettings(
                    appName = config.packageName,
                    outputDir = outputDir.get().asFile.toPath(),
                    identifier = config.bundle.identifier ?: config.identifier,
                    version = config.version,
                    mainClass = mainClass.orNull ?: build.mainClass ?: "${config.identifier}.Main",
                    icon = config.bundle.icon?.let(dir::resolve),
                    deepLinkSchemes = config.deepLinkSchemes,
                    classpath = classpath,
                ),
        )
    }

    /**
     * 前端输入。
     *
     * 前端字段在 `krema.toml` 里写作 `[build] frontend_*`，但住所在 [Frontend] ——
     * 这里是把「怎么落库」接到「喂给编排层」的唯一一处，`Build` 里不再留副本。
     */
    private fun frontend(toml: Frontend): Frontend =
        toml.copy(
            buildCommand = frontendBuildCommand.getOrElse(toml.buildCommand),
            devCommand = frontendDevCommand.getOrElse(toml.devCommand),
            devUrl = frontendDevUrl.getOrElse(toml.devUrl),
            workingDir = projectPath(),
        )

    private fun loadConfig(toml: Path): KremaConfig {
        if (!Files.isRegularFile(toml)) {
            error("找不到 krema.toml（应为 $toml）—— 先跑 kremaInit，或把 krema.configFile 指对")
        }
        val loaded = KremaConfigLoader().loadOrDefault(toml, profile.orNull)
        return loaded.copy(build = loaded.build.applyOverrides())
    }

    private fun Build.applyOverrides(): Build = copy(mainClass = this@KremaProjectTask.mainClass.orNull ?: mainClass)
}
