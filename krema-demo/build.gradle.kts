plugins {
    id("top.birthcat.kremaf.ext")
}

dependencies {
    implementation("build.krema:krema-core:0.3.2")
    annotationProcessor("build.krema:krema-processor:0.3.2")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// processor 把这个目录当普通文件系统路径写（不经 Gradle 输出目录），
// 故在配置期就取成绝对路径：任务执行期不再问 Project，配置缓存才认。
val kremaTsOutDir =
    layout.projectDirectory
        .dir("web/src")
        .asFile.absolutePath

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Akrema.ts.outDir=$kremaTsOutDir")

    // processor 绕过 Gradle 输出目录直接写文件，Gradle 因此看不见这个产物：
    // 不声明的话删掉 .d.ts 后任务仍是 UP-TO-DATE，类型文件再也回不来。
    // 只声明文件、不声明目录 —— 目录里还有手写前端源码，交给 Gradle 管会被清掉。
    outputs.file(kremaTsOutDir + "/krema-commands.d.ts").withPropertyName("kremaCommandsDts")
}

krema {
    mainClass = "com.example.kremademo.Main"

    frontend {
        buildCommand = "npm run build"
        devCommand = "npm run dev"
        devUrl = "http://localhost:5173"
    }

    bundling {
        // 写全平台想要的格式，插件构建时自动只取宿主平台的子集。
        platformFormats =
            setOf(
                top.birthcat.kremaf.ext.gradle.BundleFormat.DMG,
                top.birthcat.kremaf.ext.gradle.BundleFormat.MSI,
                top.birthcat.kremaf.ext.gradle.BundleFormat.APP_IMAGE,
            )
    }
}
