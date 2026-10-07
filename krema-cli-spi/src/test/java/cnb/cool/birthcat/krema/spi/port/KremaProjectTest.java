/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package cnb.cool.birthcat.krema.spi.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KremaProjectTest {

  @TempDir File root;

  @Test
  void carriesPathsWithoutBuildToolTypes() {
    Path dir = root.toPath();
    var project = new KremaProject(dir, dir.resolve("krema.toml"), dir.resolve("build/krema"));

    assertEquals(dir, project.projectDir());
    assertEquals(dir.resolve("krema.toml"), project.configFile());
    assertTrue(project.outputDir().startsWith(dir));
  }
}
