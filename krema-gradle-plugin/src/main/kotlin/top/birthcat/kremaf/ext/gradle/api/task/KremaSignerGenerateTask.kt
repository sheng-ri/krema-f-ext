/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.api.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.internal.tool.Ed25519Signer

/** Generates an Ed25519 key pair used to sign artifacts. */
@DisableCachingByDefault(because = "generates a random key pair; identical inputs must produce different results")
abstract class KremaSignerGenerateTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
        description = "Generate a signing key pair"
    }

    /** Root of the Krema output tree; keys are written under its keys/ directory. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Path of the generated private key; the public key is written next to it. */
    @get:OutputFile
    abstract val privateKey: RegularFileProperty

    /** Writes the key pair. */
    @TaskAction
    fun generate() {
        val keysDir =
            outputDir
                .get()
                .asFile
                .toPath()
                .resolve("keys")
        Ed25519Signer().generate(privateKey.orNull?.asFile?.toPath() ?: keysDir.resolve("krema.key"))
    }
}
