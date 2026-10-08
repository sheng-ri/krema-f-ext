plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

dependencies {
    api(libs.toml4j)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// 第二层验收的载体：真 krema-demo 子工程的编译产物。
// 测试只在它的 main 里读一行握手标志 —— 传路径而不是传 jar，免得测试去猜产物名。
val demoClasses = project(":krema-demo").layout.buildDirectory.dir("classes/java/main")

tasks.named<Test>("test") {
    dependsOn(":krema-demo:classes")
    inputs.dir(demoClasses).withPropertyName("kremaDemoClasses")
    systemProperty("krema.demo.classes", demoClasses.get().asFile.absolutePath)
}
