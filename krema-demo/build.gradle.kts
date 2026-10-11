import top.birthcat.kremaf.ext.gradle.BundleFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    id("top.birthcat.kremaf.ext")
}

dependencies {
    implementation("build.krema:krema-core:0.3.2")
    kapt("build.krema:krema-processor:0.3.2")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
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

krema {
    runtime {
        modules = setOf("java.base", "java.desktop", "java.net.http", "jdk.httpserver")
    }
    bundling {
        macosFormats = setOf(BundleFormat.APP, BundleFormat.DMG, BundleFormat.PKG)
        windowsFormats = setOf(BundleFormat.EXE, BundleFormat.MSI)
        linuxFormats = setOf(BundleFormat.APP_IMAGE, BundleFormat.DEB, BundleFormat.RPM)
    }
}
