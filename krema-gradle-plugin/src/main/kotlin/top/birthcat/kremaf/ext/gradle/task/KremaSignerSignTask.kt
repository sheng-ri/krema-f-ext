/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

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
import top.birthcat.kremaf.ext.gradle.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.tool.Ed25519Signer

/** 用私钥给产物签名。 */
@DisableCachingByDefault(because = "签名依赖私钥，且输入含密钥本身")
abstract class KremaSignerSignTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
        description = "Sign artifacts with the private key"
    }

    /** 产物根目录。 */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** 待签名产物。 */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val artifact: RegularFileProperty

    /** 私钥路径。 */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val privateKey: RegularFileProperty

    /** 签名落点。 */
    @get:OutputFile
    abstract val signature: RegularFileProperty

    @TaskAction
    fun sign() {
        Ed25519Signer().sign(
            artifact = artifact.get().asFile.toPath(),
            privateKey = privateKey.get().asFile.toPath(),
            signature = signature.get().asFile.toPath(),
        )
    }
}
