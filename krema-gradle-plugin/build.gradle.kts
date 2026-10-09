plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.toml4j)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("krema") {
            id = "top.birthcat.kremaf.ext"
            implementationClass = "top.birthcat.kremaf.ext.gradle.KremaPlugin"
        }
    }
}
