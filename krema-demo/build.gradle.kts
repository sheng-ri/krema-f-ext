import top.birthcat.kremaf.ext.gradle.domain.BundleFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.kapt)
    id("top.birthcat.kremaf.ext")
}

group = "top.birthcat.kremaf-ext"
version = providers.gradleProperty("version").getOrElse("0.1.0-SNAPSHOT")

krema {
    runtime {
        // java.logging 是 jdeps 看不见的：krema-core 的 LinuxGlobalShortcutEngine 在静态初始化里
        // 取 java.util.logging.Logger，那条引用走的是服务/反射路径，扫 jar 推不出这个模块。
        // 不手写进来，app 起完窗口就会以 NoClassDefFoundError 崩掉（见 KremaBundleRunTask 的退出码判定）。
        modules = setOf("java.base", "java.desktop", "java.logging", "java.net.http", "jdk.httpserver")
    }
    bundling {
        macosFormats = setOf(BundleFormat.APP, BundleFormat.DMG, BundleFormat.PKG)
        windowsFormats = setOf(BundleFormat.EXE, BundleFormat.MSI)
        // 本机（Linux）只出能在这里打出来的格式：AppImage 走 appimagetool（可自动下载），deb 走 jpackage。
        // rpm 需要 rpmbuild，本机没有，故不配；要打 rpm 的 runner 自己加并装好 rpmbuild ——
        // 配了而工具不在，kremaPlatformRpm 会明确失败，不静默跳过。
        linuxFormats = setOf(BundleFormat.APP_IMAGE, BundleFormat.DEB)
    }
}

dependencies {
    implementation("build.krema:krema-core:0.3.2")
    kapt("build.krema:krema-processor:0.3.2")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(25)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
        licenseHeader(
            """
            /*
             * Copyright (c) 2026 BirthCat
             * SPDX-License-Identifier: Apache 2.0
             */
            """.trimIndent(),
        )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}
// processor 把这个目录当普通文件系统路径写（不经 Gradle 输出目录），
// 故在配置期就取成绝对路径：任务执行期不再问 Project，配置缓存才认。
val kremaTsOutDir =
    layout.projectDirectory
        .dir("front/src")
        .asFile.absolutePath

// 命令注解是 Kotlin 写的，Java APT 读不到 —— 走 kapt 跑同一个 processor。
// -A 参数在 kapt 里用 arguments 块给，不在 JavaCompile 上给。
kapt {
    arguments {
        arg("krema.ts.outDir", kremaTsOutDir)
    }
}

tasks.matching { name == "kaptKotlin" }.configureEach {
    // processor 绕过 Gradle 输出目录直接写文件，Gradle 因此看不见这个产物：
    // 不声明的话删掉 .d.ts 后任务仍是 UP-TO-DATE，类型文件再也回不来。
    // 只声明文件、不声明目录 —— 目录里还有手写前端源码，交给 Gradle 管会被清掉。
    outputs.file(kremaTsOutDir + "/krema-commands.d.ts").withPropertyName("kremaCommandsDts")
}
