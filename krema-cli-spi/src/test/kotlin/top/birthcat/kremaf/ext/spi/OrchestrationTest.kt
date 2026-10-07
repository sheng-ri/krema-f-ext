/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import top.birthcat.kremaf.ext.spi.iml.delegate.BundleSupportFactoryDelegate
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioAppDeployer
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioFileSystem
import top.birthcat.kremaf.ext.spi.port.BundleSettings
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.FileSystem
import top.birthcat.kremaf.ext.spi.port.KremaProject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * 不起 Gradle 的编排验证。
 *
 * 假 [KremaProject] + 临时目录 + 假 classpath，断言产物真落盘。
 */
class OrchestrationTest {
    private val fileSystem: FileSystem = NioFileSystem()

    @Test
    fun deploysJarsAndNativeLibsIntoBundle(
        @TempDir root: File,
    ) {
        val project = KremaProject(root.toPath())
        val classpath = fakeClasspath(root.toPath())
        val bundleDir = writeBundle(root.toPath(), "MyApp.AppDir")

        NioAppDeployer(fileSystem).deploy(project, bundleDir, classpath)

        assertTrue(Files.exists(bundleDir.resolve("usr/share/krema/java/app.jar")))
        assertTrue(Files.exists(bundleDir.resolve("usr/share/krema/java/lib.jar")))
        assertTrue(Files.exists(bundleDir.resolve("usr/lib/libwebview.so")))
        assertTrue(Files.exists(bundleDir.resolve("usr/share/krema/java/classes/Main.class")))
    }

    @Test
    fun deploysIntoMacOSLayout(
        @TempDir root: File,
    ) {
        val project = KremaProject(root.toPath())
        val classpath = fakeClasspath(root.toPath())
        val bundleDir = writeBundle(root.toPath(), "MyApp.app")

        NioAppDeployer(fileSystem).deploy(project, bundleDir, classpath)

        assertTrue(Files.exists(bundleDir.resolve("Contents/Resources/Java/app.jar")))
        assertTrue(Files.exists(bundleDir.resolve("Contents/Resources/libwebview.dylib")))
    }

    @Test
    fun producesBundleLayoutWithoutBuildTool(
        @TempDir root: File,
    ) {
        val settings =
            BundleSettings(
                appName = "MyApp",
                version = "0.1.0",
                mainClass = "com.example.myapp.Main",
                outputDir = root.toPath().resolve("out"),
            )

        val appDir = BundleSupportFactoryDelegate(fileSystem).create("linux-x86_64").createProductionBundle(settings)

        assertTrue(Files.exists(appDir.resolve("AppRun")))
        assertTrue(Files.exists(appDir.resolve("usr/bin/MyApp")))
        assertTrue(Files.exists(appDir.resolve("myapp.desktop")))
        assertTrue(appDir.resolve("AppRun").toFile().canExecute())
    }

    @Test
    fun macOsBundleAsksForBundleForNativeFeatures(
        @TempDir root: File,
    ) {
        val factory = BundleSupportFactoryDelegate(fileSystem)
        val settings = BundleSettings(appName = "MyApp", outputDir = root.toPath().resolve("out"))

        val macOS = factory.create("darwin-aarch64")
        val appDir = macOS.createProductionBundle(settings)

        assertTrue(macOS.requiresBundleForNativeFeatures)
        assertTrue(Files.exists(appDir.resolve("Contents/Info.plist")))
        assertTrue(Files.exists(appDir.resolve("Contents/MacOS/MyApp")))
        assertTrue(appDir.resolve("Contents/MacOS/MyApp").toFile().canExecute())
    }

    @Test
    fun portSignatureStaysFreeOfBuildToolTypes() {
        val forbidden = listOf("org.gradle", "org.apache.maven", "build.krema.cli")
        val signatures =
            lookups.flatMap { (name, methods) ->
                methods.map { method ->
                    "$name.${method.name}: ${method.parameterTypes.joinToString { it.name }} -> ${method.returnType.name}"
                }
            }

        for (signature in signatures) {
            for (prefix in forbidden) {
                assertFalse(signature.contains(prefix), "port 签名泄漏了构建工具类型: $signature")
            }
        }
    }

    private fun fakeClasspath(root: Path): Classpath {
        val jars = root.resolve("jars")
        val classes = root.resolve("classes")
        val nativeLibs = root.resolve("native")
        Files.createDirectories(nativeLibs)
        fileSystem.writeText(jars.resolve("app.jar"), "fake-app")
        fileSystem.writeText(jars.resolve("lib.jar"), "fake-lib")
        fileSystem.writeText(classes.resolve("Main.class"), "fake-class")
        fileSystem.writeText(nativeLibs.resolve("libwebview.so"), "fake-so")
        fileSystem.writeText(nativeLibs.resolve("libwebview.dylib"), "fake-dylib")
        return Classpath(
            entries = listOf(jars.resolve("app.jar"), jars.resolve("lib.jar")),
            classOutputDir = classes,
            nativeLibDir = nativeLibs,
        )
    }

    private fun writeBundle(
        root: Path,
        name: String,
    ): Path {
        val bundleDir = root.resolve(name)
        Files.createDirectories(bundleDir)
        assertEquals(name, bundleDir.fileName.toString())
        return bundleDir
    }

    private companion object {
        private val lookups =
            listOf(
                "KremaProject" to KremaProject::class.java.methods,
                "Classpath" to Classpath::class.java.methods,
                "ClasspathResolver" to top.birthcat.kremaf.ext.spi.port.ClasspathResolver::class.java.methods,
                "FrontendBuilder" to top.birthcat.kremaf.ext.spi.port.FrontendBuilder::class.java.methods,
                "JavaCompiler" to top.birthcat.kremaf.ext.spi.port.JavaCompiler::class.java.methods,
                "AppDeployer" to top.birthcat.kremaf.ext.spi.port.AppDeployer::class.java.methods,
                "BundleSupport" to top.birthcat.kremaf.ext.spi.port.BundleSupport::class.java.methods,
                "CommandRunner" to top.birthcat.kremaf.ext.spi.port.CommandRunner::class.java.methods,
                "FileSystem" to top.birthcat.kremaf.ext.spi.port.FileSystem::class.java.methods,
            )
    }
}
