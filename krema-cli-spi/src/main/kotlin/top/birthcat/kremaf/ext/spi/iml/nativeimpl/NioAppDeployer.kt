/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.nativeimpl

import top.birthcat.kremaf.ext.spi.port.AppDeployer
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.FileSystem
import top.birthcat.kremaf.ext.spi.port.KremaProject
import java.nio.file.Path

/**
 * 把 classpath 上的 jar 与原生库放进 bundle。
 *
 * **为什么必须自己写**：上游 `BundleCommand.copyJarFiles` 硬编码 `target/` 与 `target/dependency` ——
 * Maven 专有目录约定，Gradle 不产生。这里改成从 [Classpath.entries] 逐条取，落盘位置按 bundle 名后缀查表，
 * 于是中间层既不认 `target`，也不认 `build`。
 */
class NioAppDeployer(
    private val fileSystem: FileSystem,
) : AppDeployer {
    override fun deploy(
        project: KremaProject,
        bundleDir: Path,
        classpath: Classpath,
    ): Path {
        val layout = Layout.of(bundleDir)
        classpath.entries
            .filter { it.fileName.toString().endsWith(".jar") }
            .forEach { jar -> fileSystem.copyFlat(jar, layout.jarsDirIn(bundleDir)) }
        fileSystem.copyTree(classpath.classOutputDir, layout.classesDirIn(bundleDir))
        classpath.nativeLibDir?.let { libDir ->
            fileSystem.copyMatching(libDir, layout.libsDirIn(bundleDir), NATIVE_SUFFIXES)
        }
        return bundleDir
    }

    /** bundle 目录布局，按平台约定查表。 */
    private enum class Layout(
        val jarsDir: String,
        val classesDir: String,
        val libsDir: String,
        val marker: String,
    ) {
        MACOS("Contents/Resources/Java", "Contents/Resources/Java/classes", "Contents/Resources", ".app"),
        LINUX("usr/share/krema/java", "usr/share/krema/java/classes", "usr/lib", ".AppDir"),
        WINDOWS("java", "java/classes", "lib", ""),
        ;

        fun jarsDirIn(bundleDir: Path): Path = bundleDir.resolve(jarsDir)

        fun classesDirIn(bundleDir: Path): Path = bundleDir.resolve(classesDir)

        fun libsDirIn(bundleDir: Path): Path = bundleDir.resolve(libsDir)

        companion object {
            fun of(bundleDir: Path): Layout {
                val name = bundleDir.fileName.toString()
                return entries.firstOrNull { it.marker.isNotEmpty() && name.endsWith(it.marker) } ?: WINDOWS
            }
        }
    }

    private companion object {
        private val NATIVE_SUFFIXES = listOf(".dylib", ".dll", ".so")
    }
}
