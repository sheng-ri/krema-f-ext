/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package cnb.cool.birthcat.krema.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KremaPluginTest {

  @TempDir File projectDir;

  @Test
  void appliesPluginAndRegistersExtension() throws Exception {
    Files.writeString(
        projectDir.toPath().resolve("settings.gradle.kts"), "rootProject.name = \"consumer\"\n");
    Files.writeString(
        projectDir.toPath().resolve("build.gradle.kts"),
        """
                plugins { id("cnb.cool.birthcat.krema") }

                tasks.register("showExt") {
                    val ext = project.extensions.getByName("krema")
                    doLast { println("ext=" + ext.javaClass.name) }
                }
                """);

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("showExt", "--configuration-cache")
            .build();

    assertTrue(
        result.getOutput().contains("ext=cnb.cool.birthcat.krema.gradle.KremaExtension"),
        result.getOutput());
  }
}
