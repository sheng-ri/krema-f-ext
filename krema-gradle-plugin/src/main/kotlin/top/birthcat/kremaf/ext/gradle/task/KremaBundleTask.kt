/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.BundleForm
import top.birthcat.kremaf.ext.gradle.BundleSettings
import top.birthcat.kremaf.ext.gradle.KremaProjectTask
import top.birthcat.kremaf.ext.gradle.Platform
import top.birthcat.kremaf.ext.gradle.appImageDirName
import top.birthcat.kremaf.ext.gradle.appJarName
import top.birthcat.kremaf.ext.gradle.executableSuffix
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

/**
 * 用 jpackage 产出 bundle：把 `kremaBuild` 聚好的 `lib/`（app jar + 依赖 jar + assets jar）与运行时镜像打成 app-image。
 *
 * 两种形态共用这一张类。形态只决定产物还要不要再打一份 zip；前端资源与后端聚合都在 `kremaBuild` 完成，
 * 这里只做平台打包，不碰任何资源。
 */
@DisableCachingByDefault(because = "jpackage 产物按宿主平台与本机 JDK 落盘，不做跨机器复用")
abstract class KremaBundleTask : KremaProjectTask() {
    /** 目标平台标识，形如 `linux-x86_64`。 */
    @get:Input
    abstract val target: Property<String>

    /** 分发形态：便携包出 app-image + zip，平台包只出 app-image 作为各格式的共同底座。 */
    @get:Input
    abstract val form: Property<BundleForm>

    /** `kremaBuild` 聚好的 `lib/` 目录：app jar + 依赖 jar + assets jar。 */
    @get:InputDirectory
    abstract val appLibDir: DirectoryProperty

    /** jlink 裁出的运行时镜像，即 jpackage 的 `--runtime-image`。 */
    @get:InputDirectory
    abstract val runtimeImageDir: DirectoryProperty

    /** jpackage 所在的可执行文件目录，取自 JDK toolchain 而非 PATH。 */
    @get:Internal
    abstract val jdkBinDir: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun bundle() {
        val settings = bundleSettings()
        runJpackage(appLibDir.get().asFile.toPath(), settings)
    }

    private fun runJpackage(
        libDir: Path,
        settings: BundleSettings,
    ) {
        val platform = Platform.of(target.get().substringBefore('-'))
        val dest = outputDir.get().asFile.toPath()
        Files.createDirectories(dest)
        // jpackage 见到已存在的 app-image 目录就报「目录已存在」；该目录是本 task 声明的产物，重建前清空是安全的。
        //
        // 先去只读再删：jpackage 生成的启动器带 ReadOnly 属性，而 deleteRecursively 删不掉只读文件 ——
        // 表现是 app/ 与 runtime/ 都清干净了、只剩启动器，目录因删不空而留着，下一次构建就此卡死在「目录已存在」。
        val appImage = dest.resolve(platform.appImageDirName(settings.appName)).toFile()
        appImage.walkTopDown().forEach { it.setWritable(true) }
        appImage.deleteRecursively()

        val command =
            mutableListOf(
                Path.of(jdkBinDir.get()).resolve("jpackage" + executableSuffix()).toString(),
                "--type",
                "app-image",
                "--name",
                settings.appName,
                "--input",
                libDir.toString(),
                "--main-jar",
                appJarName(settings),
                "--main-class",
                settings.mainClass,
                "--runtime-image",
                runtimeImageDir.get().asFile.absolutePath,
                "--dest",
                dest.toString(),
            )
        // 每个 --java-options 各占一个参数：值里带空格时不会被拆开，jpackage 才认得整条选项。
        javaOptions(platform).forEach { option -> command += listOf("--java-options", option) }

        val captured = ByteArrayOutputStream()
        val exit =
            execOperations
                .exec { spec ->
                    spec.commandLine(command)
                    spec.isIgnoreExitValue = true
                    spec.standardOutput = captured
                    spec.errorOutput = captured
                }.exitValue
        if (exit != 0) {
            throw GradleException("jpackage 生成 app-image 失败：\n$captured")
        }
    }

    private fun javaOptions(platform: Platform): List<String> =
        buildList {
            add("--enable-native-access=ALL-UNNAMED")
            // macOS 的应用进程必须跑在主线程上，缺这条选项启动器起不来。
            if (platform == Platform.DARWIN) add("-XstartOnFirstThread")
        }
}
