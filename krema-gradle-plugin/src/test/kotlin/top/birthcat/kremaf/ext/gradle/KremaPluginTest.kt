/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

class KremaPluginTest {
    @Test
    fun appliesPluginAndRegistersExtension(
        @TempDir projectDir: File,
    ) {
        Files.writeString(
            projectDir.toPath().resolve("settings.gradle.kts"),
            "rootProject.name = \"consumer\"\n",
        )
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """
            plugins { id("top.birthcat.kremaf.ext") }

            tasks.register("showExt") {
                val ext = project.extensions.getByName("krema")
                doLast { println("ext=" + ext.javaClass.name) }
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("showExt", "--configuration-cache")
                .build()

        assertTrue(
            result.output.contains("ext=top.birthcat.kremaf.ext.gradle.KremaExtension"),
            result.output,
        )
    }
}
