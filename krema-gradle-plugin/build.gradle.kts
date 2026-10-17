plugins {
    alias(libs.plugins.kotlin.jvm)
    `kotlin-dsl`
    alias(libs.plugins.spotless)
    alias(libs.plugins.dokka)
    `maven-publish`
}

group = "top.birthcat.kremaf-ext"
version = providers.gradleProperty("version").getOrElse("0.1.0-SNAPSHOT")

dependencies {
    implementation(libs.tomlj)

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

// 发布到共享 maven registry；凭据只从环境变量取，不进仓库。
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "cnbJars"
            url = uri("https://maven.cnb.cool/BirthCat-Dev/jars/-/packages/")
            credentials {
                username = "cnb"
                password =
                    System.getenv("CNB_TOKEN")
                        ?: providers.gradleProperty("cnbArtifactsGradlePassword").getOrElse("")
            }
        }
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        optIn = listOf("kotlin.io.path.ExperimentalPathApi")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
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
