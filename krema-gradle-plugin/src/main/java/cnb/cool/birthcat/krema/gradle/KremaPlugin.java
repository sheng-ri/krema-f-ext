/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package cnb.cool.birthcat.krema.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** 薄壳插件：注册 {@code krema} 扩展与 task 组。 */
public class KremaPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    project.getExtensions().create("krema", KremaExtension.class);
  }
}
