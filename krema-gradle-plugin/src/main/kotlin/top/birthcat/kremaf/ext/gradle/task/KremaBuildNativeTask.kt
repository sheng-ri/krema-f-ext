/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaProjectTask

/**
 * native-image。
 *
 * 真实工具链由上游票 #26 跟进；按 spec 它是 prod 档上的一个开关而不是独立 task 家族，见 #55。
 */
@DisableCachingByDefault(because = "native-image 耗时且依赖本机工具链，见 #26")
abstract class KremaBuildNativeTask : KremaProjectTask() {
    @TaskAction
    fun buildNative() {
        val app = config().packageName
        logger.warn("kremaBuildNative：$app 的 native-image 工具链接通见 #26，prod 档开关见 #55")
    }
}
