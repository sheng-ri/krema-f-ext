/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.ASSETS_DIR
import top.birthcat.kremaf.ext.gradle.BundleSettings
import top.birthcat.kremaf.ext.gradle.KremaProjectTask
import top.birthcat.kremaf.ext.gradle.MANIFEST_PATH
import top.birthcat.kremaf.ext.gradle.appJarName
import top.birthcat.kremaf.ext.gradle.assetsJarName
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * 整个应用的聚合：把后端编译产出与运行期依赖组装成 app jar，再把前端 dist 打成独立的 assets jar。
 *
 * 两个 jar 都落到 `lib/` 下，bundle task 直接拿这个目录去 jpackage。前端是「整个应用」的资源，
 * 故与后端一起在这一层聚合，而不是塞进 bundle 的打包逻辑里——bundle 只管最终平台分发。
 */
@DisableCachingByDefault(because = "聚合产物按本机 classpath 落盘，不做跨机器复用")
abstract class KremaBuildTask : KremaProjectTask() {
    /** 应用 classpath：目录形态的是主类产出与资源目录，文件形态的是运行期依赖 jar，都进 app-image。 */
    @get:Classpath
    abstract val classpathFiles: ConfigurableFileCollection

    /** 前端 dist，打进独立的 assets jar（Krema 从 classpath 的 `assets/` 读）。 */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val distFiles: ConfigurableFileCollection

    @TaskAction
    fun build() {
        val settings = bundleSettings()
        val libDir = outputDir.get().asFile.toPath().resolve(LIB_DIR)
        libDir.toFile().deleteRecursively()
        Files.createDirectories(libDir)
        assembleAppJar(libDir, settings)
        assembleAssetsJar(libDir, settings)
    }

    /** app jar：主类产出 + 资源目录，外加运行期依赖 jar 平铺进同一 `lib/`。 */
    private fun assembleAppJar(
        libDir: Path,
        settings: BundleSettings,
    ) {
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
        }
    }

    /** 独立的 assets jar：前端 dist 落到 Krema 约定的 `assets` 之下，与 app jar 平铺在同一 `lib/`。 */
    private fun assembleAssetsJar(
        libDir: Path,
        settings: BundleSettings,
    ) {
        JarOutputStream(Files.newOutputStream(libDir.resolve(assetsJarName(settings))), Manifest()).use { out ->
            val written = mutableSetOf<String>()
            distFiles.files.filter { it.exists() }.forEach { dist -> addAssets(out, written, dist.toPath()) }
        }
    }

    /** 前端产物在 assets jar 里落到 Krema 约定的 `assets` 之下，而不是 bundle 目录下的某个文件夹。 */
    private fun addAssets(
        out: JarOutputStream,
        written: MutableSet<String>,
        dist: Path,
    ) {
        if (!Files.isDirectory(dist)) {
            addEntry(out, written, "$ASSETS_DIR/${dist.fileName}", dist)
            return
        }
        Files.walk(dist).use { stream ->
            for (from in stream) {
                if (from == dist || !Files.isRegularFile(from)) continue
                addEntry(out, written, "$ASSETS_DIR/${dist.relativize(from)}", from)
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

    private companion object {
        const val LIB_DIR = "lib"
    }
}
