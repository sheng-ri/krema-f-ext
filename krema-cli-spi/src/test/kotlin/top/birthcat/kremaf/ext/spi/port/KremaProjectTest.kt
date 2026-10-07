/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KremaProjectTest {
    @Test
    fun constructsWithDirAlone(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val project = KremaProject(dir)

        assertEquals(dir, project.dir)
        assertEquals("krema-app", project.config.packageName)
        assertTrue(project.java.compile)
        assertTrue(project.frontend.enabled)
    }

    @Test
    fun nestedDefaultsStayPureData(
        @TempDir root: File,
    ) {
        val project = KremaProject(root.toPath(), KremaConfig(version = "9.9.9"))

        assertEquals("9.9.9", project.config.version)
        assertEquals("npm run build", project.config.build.frontendCommand)
        assertEquals("src-java", project.config.build.javaSourceDir)
        assertFalse(project.config.window.fullscreen)
    }
}
