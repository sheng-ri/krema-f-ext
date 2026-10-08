/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import top.birthcat.kremaf.ext.spi.iml.delegate.BundleSupportFactoryDelegate
import top.birthcat.kremaf.ext.spi.iml.delegate.DotEnvLoader
import top.birthcat.kremaf.ext.spi.iml.delegate.JdkResolver
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioAppDeployer
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioCommandRunner
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioFileSystem
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioFrontendBuilder
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioJavaCompiler
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioSigner
import top.birthcat.kremaf.ext.spi.port.AppDeployer
import top.birthcat.kremaf.ext.spi.port.BundleSupportFactory
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.ClasspathResolver
import top.birthcat.kremaf.ext.spi.port.CommandRunner
import top.birthcat.kremaf.ext.spi.port.FileSystem
import top.birthcat.kremaf.ext.spi.port.FrontendBuilder
import top.birthcat.kremaf.ext.spi.port.JavaCompiler
import top.birthcat.kremaf.ext.spi.port.KremaProject
import top.birthcat.kremaf.ext.spi.port.Signer
import java.nio.file.Path

/**
 * 编排层的 port 装配。
 *
 * 中间层六件实现在 `krema-cli-spi` 全自研，这里只把它们的依赖接上：`CommandRunner` 由 Gradle
 * 侧换 `ExecOperations` 实现（见 `GradleCommandRunner`），classpath 由 `GradleClasspathResolver` 给。
 */
class KremaPorts(
    private val commandRunner: CommandRunner,
    private val classpathResolver: ClasspathResolver,
) {
    private val fileSystem: FileSystem = NioFileSystem()

    fun files(): FileSystem = fileSystem

    fun env(project: KremaProject): Map<String, String> = DotEnvLoader(fileSystem).load(project.dir)

    fun frontend(): FrontendBuilder = NioFrontendBuilder(commandRunner)

    fun javaCompiler(outputDir: Path): JavaCompiler = NioJavaCompiler(commandRunner, JdkResolver(commandRunner), outputDir)

    fun deployer(): AppDeployer = NioAppDeployer(fileSystem)

    fun signer(): Signer = NioSigner()

    fun bundleFactory(): BundleSupportFactory = BundleSupportFactoryDelegate(fileSystem, commandRunner)

    fun classpath(project: KremaProject): Classpath = classpathResolver.resolve(project)
}
