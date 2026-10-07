plugins {
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(project(":krema-cli-spi"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("krema") {
            id = "cnb.cool.birthcat.krema"
            implementationClass = "cnb.cool.birthcat.krema.gradle.KremaPlugin"
        }
    }
}
