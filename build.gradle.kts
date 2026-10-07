plugins {
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    `maven-publish`
}

allprojects {
    group = "top.birthcat.kremaf-ext"
    version = providers.gradleProperty("version").getOrElse("0.1.0-SNAPSHOT")
}

// 发布目标：共享 maven registry。凭据只从环境变量取，不进仓库。
private val cnbRegistryUrl = "https://maven.cnb.cool/BirthCat-Dev/jars/-/packages/"

subprojects {
    apply(plugin = "com.diffplug.spotless")

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(25)
        }
    }

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
            }
        }
    }

    // 发布配置：等 java 组件就位再挂，否则 components["java"] 取不到。
    plugins.withId("java") {
        apply(plugin = "maven-publish")

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                }
            }
            repositories {
                maven {
                    name = "cnbJars"
                    url = uri(cnbRegistryUrl)
                    credentials {
                        username = "cnb"
                        password =
                            System.getenv("CNB_TOKEN")
                                ?: providers.gradleProperty("cnbArtifactsGradlePassword").getOrElse("")
                    }
                }
            }
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
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

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
