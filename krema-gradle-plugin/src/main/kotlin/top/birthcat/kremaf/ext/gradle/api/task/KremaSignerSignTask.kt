/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.api.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.internal.tool.Ed25519Signer

/** Signs an artifact with the configured Ed25519 private key. */
@DisableCachingByDefault(because = "signing depends on the private key, and the input contains the key itself")
abstract class KremaSignerSignTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
        description = "Sign artifacts with the private key"
    }

    /** Root of the Krema output tree. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Artifact to sign. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val artifact: RegularFileProperty

    /** Private key used for signing. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val privateKey: RegularFileProperty

    /** Destination of the detached signature. */
    @get:OutputFile
    abstract val signature: RegularFileProperty

    /** Writes the detached signature. */
    @TaskAction
    fun sign() {
        Ed25519Signer().sign(
            artifact = artifact.get().asFile.toPath(),
            privateKey = privateKey.get().asFile.toPath(),
            signature = signature.get().asFile.toPath(),
        )
    }
}
