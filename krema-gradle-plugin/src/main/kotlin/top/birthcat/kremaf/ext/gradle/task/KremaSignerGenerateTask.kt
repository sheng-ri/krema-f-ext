/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.tool.Ed25519Signer

/** 生成一对签名密钥。 */
@DisableCachingByDefault(because = "generates a random key pair; identical inputs must produce different results")
abstract class KremaSignerGenerateTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
        description = "Generate a signing key pair"
    }

    /** 产物根目录。 */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** 私钥落点。 */
    @get:OutputFile
    abstract val privateKey: RegularFileProperty

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
