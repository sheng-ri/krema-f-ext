/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Path

/**
 * 所有需要读 `krema.toml` 的 Krema task 的公共输入。
 *
 * Gradle 9.8 下 task action 禁访问 `Project`，所以配置一律在这里声明成 `Property`，由注册期从扩展取值。
 * 不挂 `outputDir`：打包/构建类 task 在 [KremaProjectTask] 上加，而 run task 不产出文件、只起进程，
 * 不该声明产物目录（否则与 bundle task 抢同一个输出目录）。
 */
@DisableCachingByDefault(because = "配置型基类；产物缓存由具体子类决定")
abstract class KremaConfigTask : DefaultTask() {
    /** 项目根目录，也是 `krema.toml` 的解析基准。 */
    @get:Internal
    abstract val projectDir: DirectoryProperty

    /**
     * 前端工程目录：`package.json`、锁文件与 `dist` 的解析基准，也是前端命令的工作目录。
     *
     * 与 [projectDir] 分开是因为后者是 `krema.toml` 与图标的解析基准，两者的目录并不总是同一个。
     *
     * 标 [Internal] 不标 `@InputDirectory`：后者会让 Gradle 哈希整个前端目录（含 `node_modules`，
     * 几万文件、每次 install 都变），up-to-date 与构建缓存全废。失效仍会正确发生 —— 本目录的移动
     * 改的是 `@OutputDirectory` 的位置，输出位置变了就不可能还是 UP-TO-DATE。
     */
    @get:Internal
    abstract val frontendDir: DirectoryProperty

    /**
     * `krema.toml` 的绝对路径。
     *
     * 暴露成 `String` 而不是 `RegularFileProperty`：`@InputFile` 会在 task action 之前先报
     * 「文件不存在」，那样「缺失硬报错」就退化成 Gradle 的通用校验信息，指不到 `kremaInit`。
     * 走路径字符串则把存在性判断留在 action 里，报错能带上补救指引。
     */
    @get:Input
    abstract val configFilePath: Property<String>

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
    protected fun bundleSettings(outputDir: Path): BundleSettings {
        val config = config()
        val dir = projectDir.get().asFile.toPath()
        return BundleSettings(
            appName = config.packageName,
            outputDir = outputDir,
            identifier = config.bundle.identifier ?: config.identifier,
            version = config.version,
            mainClass = config.build.mainClass ?: "${config.identifier}.Main",
            icon = config.bundle.icon?.let(dir::resolve),
            deepLinkSchemes = config.deepLinkSchemes,
        )
    }
}

/** 公共输入的默认值：扩展里给了就用扩展的，没给就取约定位置。 */
internal fun KremaConfigTask.configureProject(ext: KremaExtension) {
    projectDir.convention(project.layout.projectDirectory)
    configFilePath.convention(
        ext.configFile.orElse(project.layout.projectDirectory.file("krema.toml")).map { it.asFile.absolutePath },
    )
    configFileSetExplicitly.convention(ext.configFile.isPresent)
    configFilePresent.convention(
        project.provider {
            java.nio.file.Files
                .exists(
                    java.nio.file.Path
                        .of(configFilePath.get()),
                )
        },
    )
    mainClass.convention(ext.mainClass)
}

/** 主 source set 的编译产物与运行期 classpath，供 #55 把应用进程打进 bundle。 */
data class KremaApplicationClasspath(
    val classesDirs: org.gradle.api.file.FileCollection,
    val resourcesDir: java.io.File?,
    val runtimeClasspath: org.gradle.api.artifacts.Configuration,
)

/**
 * 应用进程运行所需的 classpath 来源。
 *
 * 插件不假设编译方言：Java 与 Kotlin 的产出都落在 `sourceSets["main"]` 的输出上，
 * 因此直接取 Gradle 标准位置，不认插件自造的源码目录。
 */
fun Project.kremaApplicationClasspath(): KremaApplicationClasspath {
    val main = extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java).sourceSets.getByName("main")
    return KremaApplicationClasspath(
        classesDirs = main.output.classesDirs,
        resourcesDir = main.output.resourcesDir,
        runtimeClasspath = configurations.getByName("runtimeClasspath"),
    )
}
