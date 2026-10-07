plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":krema-cli-spi"))

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
