/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import top.birthcat.kremaf.ext.spi.iml.delegate.DotEnvLoader
import top.birthcat.kremaf.ext.spi.iml.delegate.KremaConfigLoader
import top.birthcat.kremaf.ext.spi.iml.delegate.NativeLibraryFinder
import top.birthcat.kremaf.ext.spi.iml.delegate.Platform
import top.birthcat.kremaf.ext.spi.iml.delegate.PlatformDetector
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioAppDeployer
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioFileSystem
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.KremaProject
import java.io.File

/**
 * 中间层的纯 JUnit 验证：假 KremaProject + 临时目录 + 假 classpath，断言产物落盘。
 */
class OrchestrationTest {
    private val fs = NioFileSystem()

    @Test
    fun deploysJarsAndNativeLibsIntoBundle(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val jarsDir = dir.resolve("jars").also { fs.writeText(it.resolve("app.jar"), "jar") }
        val classesDir = dir.resolve("classes").also { fs.writeText(it.resolve("Main.class"), "x") }
        val nativeDir = dir.resolve("native").also { fs.writeText(it.resolve("libkrema_webview.so"), "so") }
        val bundleDir = dir.resolve("App").also { fs.writeText(it.resolve("App.bat"), "@echo off") }

        val classpath = Classpath(listOf(jarsDir.resolve("app.jar")), classesDir, nativeDir)
        NioAppDeployer(fs).deploy(KremaProject(dir), bundleDir, classpath)

        assertTrue(fs.readText(bundleDir.resolve("java/app.jar")) != null)
        assertTrue(fs.readText(bundleDir.resolve("java/classes/Main.class")) != null)
        assertTrue(fs.list(bundleDir.resolve("lib")).isNotEmpty())
    }

    @Test
    fun findsNativeLibraryByExactName(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val sub = dir.resolve("${PlatformDetector.detect().name.lowercase()}/${PlatformDetector.getArch()}")
        val expected = PlatformDetector.formatLibraryName("webview")
        fs.writeText(sub.resolve(expected), "so")

        val finder = NativeLibraryFinder(fs)

        assertEquals(sub, finder.findIn(dir))
        assertEquals(sub.resolve(expected), finder.findLibrary(listOf(dir)))
    }

    @Test
    fun missesMisnamedNativeLibrary(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        if (PlatformDetector.detect() != Platform.LINUX) return
        fs.writeText(dir.resolve("libkrema_webview.so"), "so")

        assertNull(NativeLibraryFinder(fs).findLibrary(listOf(dir)))
    }

    @Test
    fun loadsConfigAndEnvFromDisk(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        fs.writeText(
            dir.resolve("krema.toml"),
            """
            [package]
            name = "demo"
            version = "1.2.3"
            """.trimIndent(),
        )
        fs.writeText(dir.resolve(".env"), "API_URL=http://a\n# comment\n")
        fs.writeText(dir.resolve(".env.dev"), "API_URL=http://b\n")

        val config = KremaConfigLoader().loadOrDefault(dir.resolve("krema.toml"))
        assertEquals("demo", config.packageName)
        assertEquals("1.2.3", config.version)

        val env = DotEnvLoader(fs).load(dir, "dev")
        assertEquals("http://b", env["API_URL"])
    }
}
