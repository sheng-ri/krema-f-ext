/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaProjectTask

/** 生成一对签名密钥。 */
@DisableCachingByDefault(because = "生成随机密钥，同一输入必须产出不同结果")
abstract class KremaSignerGenerateTask : KremaProjectTask() {
    /** 私钥落点。 */
    @get:OutputFile
    abstract val privateKey: RegularFileProperty

    @TaskAction
    fun generate() {
        kremaProject()
        val keysDir =
            outputDir
                .get()
                .asFile
                .toPath()
                .resolve("keys")
        ports().signer().generate(privateKey.orNull?.asFile?.toPath() ?: keysDir.resolve("krema.key"))
    }
}
