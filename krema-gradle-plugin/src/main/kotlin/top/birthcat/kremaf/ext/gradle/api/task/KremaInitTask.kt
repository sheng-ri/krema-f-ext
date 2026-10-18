/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.api.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.internal.InitSettings
import top.birthcat.kremaf.ext.gradle.internal.TemplateGenerator
import top.birthcat.kremaf.ext.gradle.internal.config.DEFAULT_APP_NAME

/**
 * Generates the Krema project scaffold.
 *
 * The only task that does not require krema.toml to exist, because the scaffold produces it.
 */
@DisableCachingByDefault(because = "writes the scaffold into the user's project directory; the output is not owned by this task")
abstract class KremaInitTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
        description = "Generate the Krema project scaffold (krema.toml + main class)"
    }

    /** Directory the scaffold is written into; defaults to the project directory. */
    @get:Internal
    abstract val projectDir: DirectoryProperty

    /** Same directory as [projectDir], declared separately as the task output. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Application name; also drives the generated package name and artifact name. */
    @get:Input
    @get:Optional
    abstract val appName: Property<String>

    /** Writes the scaffold files. */
    @TaskAction
    fun generate() {
        TemplateGenerator().generate(
            projectDir.get().asFile.toPath(),
            InitSettings.defaults(appName.getOrElse(DEFAULT_APP_NAME)),
        )
    }
}
