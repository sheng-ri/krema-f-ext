/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
@file:JvmName("Main")

package com.example.kremademo

import build.krema.core.Krema
import build.krema.core.platform.NativeLibraryLoader

/**
 * demo 的主类：把应用身份、窗口尺寸与命令一次性交给 Krema。
 *
 * 命令对象在进程内只有这一个实例，所以 [NoteCommands] 的字段就是应用状态本身 ——
 * 这是 demo 故意选简单形态的原因：状态放哪、前端怎么读到，一眼能看完。
 */
fun main() {
    if (System.getProperty("os.name").lowercase().contains("win")) {
        NativeLibraryLoader.load("libwinpthread-1")
    }
    val app =
        Krema
            .app()
            .title("Krema Demo")
            .identifier("com.example.kremademo")
            .size(1280, 1024)
            .prodAssets("/assets")
            .commands(NoteCommands())
    // devUrl 一旦非空，krema-core 就整个走 dev 分支、不再起 AssetServer。
    // 故只在真有 dev server 时才设：打包出来的 app 不带这个变量，走 prodAssets 出前端。
    System.getenv("KREMA_DEV_URL")?.let { app.devUrl(it) }
    app.run()
}
