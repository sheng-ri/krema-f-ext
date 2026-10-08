/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.nio.file.Path

/**
 * bundle 产物形态的成员，一平台的词表就是这个枚举的全部成员。
 *
 * 词表由本仓定义，不是 jpackage `--type` 的镜像：一个形态由 jpackage 打、本仓手搓还是
 * 外部工具打，是实现选择，不上浮到词表（见 #32）。
 *
 * @property formatName 用户读到、DSL 里写下的成员名，与 `krema.toml` / DSL 的拼写一致（全小写）
 */
enum class BundleFormat(
    val formatName: String,
) {
    /** jpackage `--type app-image`：自包含目录树，解出来就能跑，但没压成单文件。 */
    APP_IMAGE("app-image"),

    /** macOS 手搓的 `.app` 目录，dev 流程硬依赖它，走不了 jpackage。 */
    APP("app"),

    /** jpackage `--type exe`：Windows 安装器。 */
    EXE("exe"),

    /** jpackage `--type msi`：Windows 安装器。 */
    MSI("msi"),

    /** jpackage `--type deb`：Linux 安装器。 */
    DEB("deb"),

    /** jpackage `--type rpm`：Linux 安装器。 */
    RPM("rpm"),

    /** jpackage `--type dmg`：macOS 磁盘映像。 */
    DMG("dmg"),

    /** jpackage `--type pkg`：macOS 安装器。 */
    PKG("pkg"),
    ;

    /**
     * 产物是不是**装完才能跑**。
     *
     * 判据不看扩展名，看「手上拿到的这个文件能不能直接起进程」：安装器要先落盘到系统里，
     * 落的路径由安装程序决定，所以交付物本身不可 exec。这与谁打的无关 —— `app` 是手搓的、
     * `app-image` 是 jpackage 打的，两个都能直接跑。
     */
    val installable: Boolean
        get() =
            when (this) {
                APP_IMAGE, APP -> false
                EXE, MSI, DEB, RPM, DMG, PKG -> true
            }

    /**
     * 产物形态属于哪一族。
     *
     * 这是「`kremaBundleProdRun` 到底能跑哪个产物」的判据来源，也是 [#34] 验收矩阵按格式
     * 挑代表产物的依据。
     */
    val productShape: ProductShape
        get() = if (installable) ProductShape.DISTRIBUTABLE else ProductShape.RUNNABLE

    /** 一族里的非安装器成员，直接可跑；空集表示这一族全是安装器。 */
    val runnableAlternatives: Set<BundleFormat>
        get() = entries.filterTo(mutableSetOf()) { it.productShape != ProductShape.DISTRIBUTABLE }

    companion object {
        /** 词表默认值：每一族各取一个（`app-image` / `app` / `exe` / `deb` / `dmg`）。 */
        val PRESETS: Set<BundleFormat> = setOf(APP_IMAGE, APP, EXE, DEB, DMG)

        /**
         * 按成员名查格式，名字即 DSL 上写下的那个字符串。
         *
         * @param formatName 成员名，如 `deb`
         * @return 对应格式，无此成员时返回 null
         */
        fun of(formatName: String): BundleFormat? = entries.firstOrNull { it.formatName == formatName }
    }
}

/**
 * 产物形态按「用户拿到手之后要做什么」分族，三族一条轴。
 *
 * 已从 [BundleFormat] 实际收编的最小项。
 */
enum class ProductShape {
    /** 中间产物：本仓产线上的一站，用户通常拿不到手。 */
    RAW,

    /** 拿上手就能跑：目录树或可执行启动器，不必先安装。 */
    RUNNABLE,

    /** 拿上手要装（或要挂载）之后才谈得上跑。 */
    DISTRIBUTABLE,
    ;

    /** 这一族直接可跑吗 —— 即 [BundleFormat.runnableAlternatives] 非空的那些族。 */
    val directlyRunnable: Boolean
        get() = this != DISTRIBUTABLE
}

/**
 * run 类 task 的守门。
 *
 * 判据是「产物形态」不是扩展名：`exe` / `msi` / `deb` / `rpm` / `dmg` / `pkg` 都是安装器，
 * 产物本身没有可 exec 的入口，得先装（`dmg` 是先挂载）。
 *
 * **硬报错**，不做静默跳过：可选路径 = 分支爆炸，且用户会以为 task 成功就等于跑起来了。
 *
 * @param format 被要求跑的格式
 * @param bundlePath 产物路径，只用于把话说清
 * @throws UnsupportedOperationException 该格式只能装完再跑
 */
fun requireRunnable(
    format: BundleFormat,
    bundlePath: Path,
) {
    if (!format.installable) return
    throw UnsupportedOperationException(
        "格式 ${format.formatName} 的产物是安装器（$bundlePath），装上（dmg 是先挂载）之后才谈得上跑 —— " +
            "kremaBundleProdRun 只跑可跑子集。要打能让 run 直接跑起来的产物，请把 format 换成 " +
            format.runnableAlternatives.joinToString(" / ") { it.formatName },
    )
}
