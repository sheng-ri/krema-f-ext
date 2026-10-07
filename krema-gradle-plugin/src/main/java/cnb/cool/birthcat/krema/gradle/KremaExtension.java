/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package cnb.cool.birthcat.krema.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;

/** {@code krema { }} 扩展：暴露全局默认值。 */
public abstract class KremaExtension {

  /** 项目根目录下的 krema.toml。 */
  public abstract RegularFileProperty getConfigFile();

  /** 产物根目录，默认 build/krema。 */
  public abstract DirectoryProperty getOutputDir();
}
