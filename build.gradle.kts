plugins {
    alias(libs.plugins.spotless) apply false
    `maven-publish`
}

allprojects {
    group = "cnb.cool.birthcat.krema"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
            }
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat()
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

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
