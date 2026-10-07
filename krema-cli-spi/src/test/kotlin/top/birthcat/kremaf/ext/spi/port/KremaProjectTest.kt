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
    fun carriesPathsWithoutBuildToolTypes(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val project = KremaProject(dir)

        assertEquals(dir, project.dir)
        assertEquals("krema-app", project.config.packageName)
        assertFalse(project.toString().contains("org.gradle"))
        assertFalse(project.toString().contains("org.apache.maven"))
    }

    @Test
    fun classpathJoinsEntriesWithPlatformSeparator(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val classpath = Classpath(listOf(dir.resolve("a.jar"), dir.resolve("b.jar")), dir.resolve("classes"))

        assertEquals(
            listOf(dir.resolve("a.jar"), dir.resolve("b.jar")).joinToString(File.pathSeparator),
            classpath.joined(),
        )
        assertTrue(classpath.nativeLibDir == null)
    }
}
