/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.toolchain.JavaToolchainService
import top.birthcat.kremaf.ext.gradle.task.KremaBuildTask
import top.birthcat.kremaf.ext.gradle.task.KremaBundleRunTask
import top.birthcat.kremaf.ext.gradle.task.KremaBundleTask
import top.birthcat.kremaf.ext.gradle.task.KremaFrontendBuildTask
import top.birthcat.kremaf.ext.gradle.task.KremaInitTask
import top.birthcat.kremaf.ext.gradle.task.KremaRuntimeImageTask
import top.birthcat.kremaf.ext.gradle.task.KremaSignerGenerateTask
import top.birthcat.kremaf.ext.gradle.task.KremaSignerSignTask

/** 目标平台标识，形如 `linux-x86_64`。同时是便携包 zip 名里的 target 段，故只算一次。 */
private val ZIP_TARGET: String = "${PlatformDetector.detect().name.lowercase()}-${PlatformDetector.getArch()}"

internal fun Project.registerKremaTasks() {
    val ext = extensions.getByType(KremaExtension::class.java)

    val cleanup =
        gradle.sharedServices.registerIfAbsent("kremaProcessCleanup", KremaProcessCleanupService::class.java) {}

    tasks.register(KremaTaskNames.INIT, KremaInitTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "生成 Krema 项目骨架（krema.toml + 主类）"
        task.projectDir.convention(layout.projectDirectory)
        task.outputDir.convention(layout.projectDirectory)
    }

    tasks.register(KremaTaskNames.FRONTEND_BUILD, KremaFrontendBuildTask::class.java) { task ->
        // 用户面向的前端构建 task：进 krema 分组，也出现在 KremaTaskNames.ALL（与 kremaBuild 一并构成公开 family）。
        task.group = KremaTaskNames.GROUP
        // 前端产物落在 krema.toml 的 out_dir（默认 dist/，相对项目根），也就是前端工具链自己的落点。
        // 插件只能按它去读：读 build/krema 下的目录等于读一个前端工具从不写的地方，prod 的 app jar 会空着 web/。
        task.description = "跑前端生产构建（npm 命令），产物目录取自 krema.toml 的 out_dir 并声明成可缓存输出"
        task.configureProject(ext)
        task.buildCommand.convention(ext.frontend.buildCommand)
        task.distDir.convention(
            providers
                .provider { kremaConfig(ext).build.outDir }
                .map { layout.projectDirectory.dir(it) },
        )
        // 前端输入在项目根解析；哪些锁文件存在就收集哪些，缺失的候选自然不在集合里。
        val projectDir = project.layout.projectDirectory
        task.frontendInputs.from(
            listOf("package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml")
                .map { projectDir.file(it) },
        )
    }

    tasks.register(KremaTaskNames.BUILD, KremaBuildTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "让 Gradle 编译主 source set，并把前端 dist 收进 build/krema"
        task.configureProject(ext)
        // 编译交回 Gradle：kremaBuild 跑在 compileJava / processResources 之后。
        task.dependsOn(tasks.named("classes"))
        // 前端 dist 是 kremaBuild 的共用前置：声明成依赖后它才成为可缓存、UP-TO-DATE 的产出。
        task.dependsOn(tasks.named(KremaTaskNames.FRONTEND_BUILD))
    }

    // dev / prod 两张打包 task：都先跑 kremaBuild（编译 + 前端），dev 额外收最小形态、不打包 dist。
    val classpath = kremaApplicationClasspath()

    tasks.register(KremaTaskNames.RUNTIME_IMAGE, KremaRuntimeImageTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "用 jdeps 推导模块、jlink 裁出最小运行时镜像（build/krema/runtime）"
        task.configureProject(ext)
        task.runtimeImageDir.convention(task.outputDir.dir("runtime"))
        task.jdkBinDir.set(jdkBinDir())
        task.classpathFiles.from(classpath.classesDirs)
        task.classpathFiles.from(classpath.runtimeClasspath)
        // 模块推导读的是编译产出，镜像因此建立在 classes 之上。
        task.dependsOn(tasks.named("classes"))
    }

    val distDevDir = registerBundle(KremaTaskNames.BUNDLE_DIST_DEV, BundleForm.DIST, dev = true, ext, classpath)
    val distProdDir = registerBundle(KremaTaskNames.BUNDLE_DIST_PROD, BundleForm.DIST, dev = false, ext, classpath)
    registerBundle(KremaTaskNames.BUNDLE_PLATFORM_DEV, BundleForm.PLATFORM, dev = true, ext, classpath)
    registerBundle(KremaTaskNames.BUNDLE_PLATFORM_PROD, BundleForm.PLATFORM, dev = false, ext, classpath)

    // run 调试的是便携包，故拿 dist 那两张的产物目录；直接复用打包 task 算出的目录，
    // 不自己再拼一遍 `krema/dist/{tier}`——两处各拼一次，目录约定一改就会有一边悄悄指错。
    registerRun(
        KremaTaskNames.BUNDLE_RUN_DEV,
        KremaTaskNames.BUNDLE_DIST_DEV,
        distDevDir,
        dev = true,
        ext,
        cleanup,
    )
    registerRun(
        KremaTaskNames.BUNDLE_RUN_PROD,
        KremaTaskNames.BUNDLE_DIST_PROD,
        distProdDir,
        dev = false,
        ext,
        cleanup,
    )

    tasks.register(KremaTaskNames.SIGNER_GENERATE, KremaSignerGenerateTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "生成一对签名密钥"
        task.configureProject(ext)
        task.privateKey.convention(task.outputDir.file("keys/krema.key"))
    }

    tasks.register(KremaTaskNames.SIGNER_SIGN, KremaSignerSignTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = "用私钥给产物签名"
        task.configureProject(ext)
    }
}

/** 注册一张打包 task，返回它的产物目录，供 run task 复用同一个 app-image 落点。 */
private fun Project.registerBundle(
    name: String,
    form: BundleForm,
    dev: Boolean,
    ext: KremaExtension,
    classpath: KremaApplicationClasspath,
): Provider<Directory> {
    val tier = if (dev) "dev" else "prod"
    val kremaRoot: Provider<Directory> = ext.outputDir.orElse(layout.buildDirectory.dir("krema"))
    // 产物路径按「形态 × 档位」固定成 build/krema/{dist|platform}/{dev|prod}，CI 的上传规则照这个写死。
    val bundleDir: Provider<Directory> = kremaRoot.map { it.dir(form.id).dir(tier) }

    tasks.register(name, KremaBundleTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description = bundleDescription(form, dev)
        task.configureProject(ext)
        task.outputDir.convention(bundleDir)
        task.target.convention(currentTarget())
        task.form.convention(form)
        task.dev.convention(dev)
        // 应用 classpath 直接取主 source set 产出与运行期依赖：classesDirs 是编译产出目录，
        // runtimeClasspath 含运行期依赖 jar 与（存在的）资源目录。
        task.classpathFiles.from(classpath.classesDirs)
        task.classpathFiles.from(classpath.runtimeClasspath)
        if (dev) {
            task.distFiles.setFrom(emptyList<Any>())
        } else {
            task.distFiles.from(
                tasks.named(KremaTaskNames.FRONTEND_BUILD, KremaFrontendBuildTask::class.java).map { it.distDir },
            )
        }
        // app-image 自带 jlink 裁出的最小运行时，因此打包建立在编译、前端与运行时镜像三者之上。
        task.runtimeImageDir.set(
            tasks
                .named(KremaTaskNames.RUNTIME_IMAGE, KremaRuntimeImageTask::class.java)
                .flatMap { it.runtimeImageDir },
        )
        task.jdkBinDir.set(jdkBinDir())
        task.dependsOn(tasks.named(KremaTaskNames.BUILD))
        task.dependsOn(tasks.named(KremaTaskNames.RUNTIME_IMAGE))
    }

    if (!form.zipped) return bundleDir
    registerPortableZip(name, bundleDir, ext)
    return bundleDir
}

/**
 * 便携包 zip：把刚打出的 app-image 原样打包，解压即用。
 *
 * 用 Gradle 的 [Zip] 而不是自己拼 zip 字节 —— `copy { into("x.zip") }` 只会建出同名目录。
 * zip 由打包 task `finalizedBy` 而不是独立入口：先有 app-image 才谈得上打包，
 * 而 `Zip` 在源为空时会整张跳过，让它反过来依赖 app-image 产出即可。
 */
private fun Project.registerPortableZip(
    bundleTaskName: String,
    bundleDir: Provider<Directory>,
    ext: KremaExtension,
) {
    val zipName = "$bundleTaskName${KremaTaskNames.ZIP_SUFFIX}"
    tasks.register(zipName, Zip::class.java) { task ->
        // zip 是打包 task 的收尾，不进 krema 分组：用户面向的入口仍是那四张打包 task。
        task.group = null
        task.description = "把 app-image 打成便携包 zip"
        task.archiveFileName.set(
            providers
                .provider { bundleNaming(ext) }
                .map { "$it-$ZIP_TARGET.zip" },
        )
        task.destinationDirectory.set(bundleDir)
        // 源目录与 zip 落地目录是同一个，Gradle 不会自动排掉产物本身：
        // 不显式排除，zip 会先建出空壳再把它当输入收进去，用户解开多出一个坏掉的同名 zip。
        val archiveFile = task.archiveFile
        task.from(bundleDir) { spec -> spec.exclude { it.file == archiveFile.get().asFile } }
        task.dependsOn(tasks.named(bundleTaskName))
    }
    tasks.named(bundleTaskName) { task -> task.finalizedBy(zipName) }
}

/** 便携包 zip 的文件名前缀 `<app>-<version>`：名字带版本，CI 才认得出该传哪一份。 */
private fun Project.bundleNaming(ext: KremaExtension): String {
    val config = kremaConfig(ext)
    return "${config.packageName}-${config.version}"
}

/**
 * 读 `krema.toml`。
 *
 * 走 `krema { }` 显式指定的路径，没指定才用项目根下的约定位置 —— 与 [KremaConfigTask.configFilePath] 的取法一致。
 * 注册期就要用到配置（如前端产物目录），故这里在配置期直接读文件，而不是等 task 执行。
 */
private fun Project.kremaConfig(ext: KremaExtension): KremaConfig {
    val toml =
        ext.configFile
            .orElse(layout.projectDirectory.file("krema.toml"))
            .get()
            .asFile
            .toPath()
    return KremaConfigLoader().loadOrDefault(toml)
}

private fun bundleDescription(
    form: BundleForm,
    dev: Boolean,
): String {
    val tier = if (dev) "dev 档" else "prod 档"
    val formText = if (form == BundleForm.DIST) "便携包（app-image + zip）" else "平台包底座（app-image）"
    return "生成$tier$formText，产物自带 jlink 裁出的最小运行时"
}

private fun Project.registerRun(
    name: String,
    bundleTask: String,
    bundleDir: Provider<Directory>,
    dev: Boolean,
    ext: KremaExtension,
    cleanup: Provider<KremaProcessCleanupService>,
) {
    tasks.register(name, KremaBundleRunTask::class.java) { task ->
        task.group = KremaTaskNames.GROUP
        task.description =
            if (dev) {
                "起前端 dev server 并跑 dev 档应用（长驻，不进 CI 门禁）"
            } else {
                "跑 prod 档应用（长驻，不进 CI 门禁）"
            }
        task.configureProject(ext)
        // 便携包是唯一带启动器、跨档位通用的形态，run task 因此认它而不是平台包；
        // 目录直接取自那张打包 task，保证起的就是刚打出来的那份 app-image。
        task.bundleDir.convention(bundleDir)
        task.target.convention(currentTarget())
        task.dev.convention(dev)
        task.devCommand.convention(ext.frontend.devCommand)
        task.cleanupService.set(cleanup)
        task.usesService(cleanup)
        // 先打包再起进程，找不到 bundle 时直接报错而不是凭空 launch。
        task.dependsOn(tasks.named(bundleTask))
    }
}

private fun currentTarget(): String = ZIP_TARGET

/** 编译所用 JDK 的 `bin` 目录：jlink、jdeps 与 jpackage 跟着编译用的 JDK 走，而不是跟着 PATH 走。 */
private fun Project.jdkBinDir(): Provider<String> {
    val toolchains = extensions.getByType(JavaToolchainService::class.java)
    val languageVersion = extensions.getByType(JavaPluginExtension::class.java).toolchain.languageVersion
    return toolchains
        .launcherFor { spec -> spec.languageVersion.set(languageVersion) }
        .map {
            it.metadata.installationPath
                .dir("bin")
                .asFile.absolutePath
        }
}
