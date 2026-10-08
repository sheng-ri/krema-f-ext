/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioAppDeployer
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioFileSystem
import top.birthcat.kremaf.ext.spi.port.BundleSettings
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.KremaProject
import top.birthcat.kremaf.ext.spi.port.LaunchSettings
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path

/**
 * 验收第二层：真起一次 `krema-demo` 进程，断言握手行出现。
 *
 * 走的是真产线：`createBundle` 出 AppDir → `deploy` 把 demo 的 class 铺进去 → `run` 起进程判握手。
 * 测的是**子进程 stdout 流**，不是退出码 —— 窗口活多久 = 退出码迟迟不落。
 * 平台限定 Linux：CI 就是 Linux（真开窗口不进 CI，见地图 Out of scope）。
 */
class BundleRunHandshakeTest {
    @Test
    fun demoHandshakeLineAppearsWhenProcessStarts(
        @TempDir root: Path,
    ) {
        assumeTrue(PlatformDetector.detect() == Platform.LINUX, "第二层只在 Linux CI 真跑")

        val demoClasses = Path.of(System.getProperty("krema.demo.classes") ?: return)
        assumeTrue(demoClasses.toFile().isDirectory, "krema-demo 没编译出来：$demoClasses")

        val fs = NioFileSystem()
        val settings =
            BundleSettings(
                appName = "krema-demo",
                outputDir = root,
                mainClass = "com.example.demo.Main",
                version = "0.1.0",
                identifier = "com.example.demo",
            )
        val factory = BundleSupportFactoryDelegate(fs)
        val bundleDir = factory.create("linux-x86_64").createBundle(settings, dev = false)
        NioAppDeployer(fs).deploy(KremaProject(root, bundle = settings), bundleDir, Classpath(emptyList(), demoClasses))

        val out = ByteArrayOutputStream()
        val original = System.out
        val process =
            try {
                System.setOut(PrintStream(out, true, Charsets.UTF_8))
                factory
                    .create("linux-x86_64")
                    .run(
                        LaunchSettings(
                            bundlePath = bundleDir,
                            classpath = emptyList(),
                            mainClass = "com.example.demo.Main",
                            handshake = true,
                        ),
                    )
            } finally {
                System.setOut(original)
            }

        process.waitFor()
        assertTrue(
            out.toString(Charsets.UTF_8).contains(HANDSHAKE_LINE),
            "krema-demo 的 stdout 里没出现握手行：\n${out.toString(Charsets.UTF_8)}",
        )
    }
}
