/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.BundleForm
import top.birthcat.kremaf.ext.gradle.BundleSettings
import top.birthcat.kremaf.ext.gradle.KremaProjectTask
import top.birthcat.kremaf.ext.gradle.Platform
import top.birthcat.kremaf.ext.gradle.appImageDirName
import top.birthcat.kremaf.ext.gradle.executableSuffix
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.inject.Inject

/**
 * 用 jpackage 产出 bundle：先把主类产出与运行期依赖组装成 app jar，再以裁好的运行时镜像生成 app-image。
 *
 * 四张打包 task（[BundleForm] × dev / prod）共用这一张类。形态只决定产物还要不要再打一份 zip，
 * 档位只决定 app jar 里带不带前端产物：dev 走 devUrl 拉前端，带上前端会让两档失去差别。
 * 产物自带 [runtimeImageDir] 那份最小运行时，使用方机器不必预装 JDK。
 *
 * app jar 在打包期现装而不是挂成 `processResources` 的输入，正是为了守住上面这条档位差别。
 */
@DisableCachingByDefault(because = "jpackage 产物按宿主平台与本机 JDK 落盘，不做跨机器复用")
abstract class KremaBundleTask : KremaProjectTask() {
    /** 目标平台标识，形如 `linux-x86_64`。 */
    @get:Input
    abstract val target: Property<String>

    /** 分发形态：便携包出 app-image + zip，平台包只出 app-image 作为各格式的共同底座。 */
    @get:Input
    abstract val form: Property<BundleForm>

    /** 是否 dev 档（决定 app jar 里带不带前端产物）。 */
    @get:Input
    abstract val dev: Property<Boolean>

    /** 应用 classpath：目录形态的是主类产出与资源目录，文件形态的是运行期依赖 jar，都进 app-image。 */
    @get:Classpath
    abstract val classpathFiles: ConfigurableFileCollection

    /** prod 档要打进 app jar 的前端产物；dev 档为空集合。 */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val distFiles: ConfigurableFileCollection

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
        val libDir = temporaryDir.toPath().resolve(LIB_DIR)
        libDir.toFile().deleteRecursively()
        assembleLib(libDir, settings)
        runJpackage(libDir, settings)
    }

    /** app jar 名，与 `--main-jar` 对应；jpackage 会把它搬进 app-image 的 `lib/app`。 */
    private fun appJarName(settings: BundleSettings): String = "${settings.appName}.jar"

    private fun assembleLib(
        libDir: Path,
        settings: BundleSettings,
    ) {
        Files.createDirectories(libDir)
        classpathFiles.files.filter { it.isFile }.forEach { jar ->
            Files.copy(jar.toPath(), libDir.resolve(jar.name), REPLACE_EXISTING)
        }

        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name.MAIN_CLASS] = settings.mainClass
            }
        JarOutputStream(Files.newOutputStream(libDir.resolve(appJarName(settings))), manifest).use { out ->
            // JarOutputStream 已经先写了 manifest：登记它，资源目录里同名文件才不会被写第二遍。
            val written = mutableSetOf(MANIFEST_PATH)
            classpathFiles.files.filter { it.isDirectory && it.exists() }.forEach { dir ->
                val root = dir.toPath()
                Files.walk(root).use { stream ->
                    for (from in stream) {
                        if (from == root || !Files.isRegularFile(from)) continue
                        addEntry(out, written, root.relativize(from).toString(), from)
                    }
                }
            }
            if (!dev.get()) {
                distFiles.files.filter { it.exists() }.forEach { dist -> addAssets(out, written, settings, dist.toPath()) }
            }
        }
    }

    /** 前端产物在 app jar 里落到资产路径（默认 `web`）之下，而不是 bundle 目录下的某个文件夹。 */
    private fun addAssets(
        out: JarOutputStream,
        written: MutableSet<String>,
        settings: BundleSettings,
        dist: Path,
    ) {
        if (!Files.isDirectory(dist)) {
            addEntry(out, written, "${settings.assetsPath}/${dist.fileName}", dist)
            return
        }
        Files.walk(dist).use { stream ->
            for (from in stream) {
                if (from == dist || !Files.isRegularFile(from)) continue
                addEntry(out, written, "${settings.assetsPath}/${dist.relativize(from)}", from)
            }
        }
    }

    private fun addEntry(
        out: JarOutputStream,
        written: MutableSet<String>,
        name: String,
        from: Path,
    ) {
        val entryName = name.replace(File.separatorChar, '/')
        // 多方言的 classesDirs 可能落在同名相对路径上，app jar 里只收第一份。
        if (!written.add(entryName)) return
        out.putNextEntry(JarEntry(entryName))
        Files.copy(from, out)
        out.closeEntry()
    }

    private fun runJpackage(
        libDir: Path,
        settings: BundleSettings,
    ) {
        val platform = Platform.of(target.get().substringBefore('-'))
        val dest = outputDir.get().asFile.toPath()
        Files.createDirectories(dest)
        // jpackage 见到已存在的 app-image 目录就报「目录已存在」；该目录是本 task 声明的产物，重建前清空是安全的。
        dest.resolve(platform.appImageDirName(settings.appName)).toFile().deleteRecursively()

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

    private companion object {
        const val LIB_DIR = "lib"
        const val MANIFEST_PATH = "META-INF/MANIFEST.MF"
    }
}
