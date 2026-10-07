/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class KremaProjectTest {
    @Test
    fun carriesPathsWithoutBuildToolTypes(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val project = KremaProject(dir, dir.resolve("krema.toml"), dir.resolve("build/krema"))

        assertEquals(dir, project.projectDir)
        assertEquals(dir.resolve("krema.toml"), project.configFile)
        assertTrue(project.outputDir.startsWith(dir))
    }
}
