/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.nio.file.Path

/**
 * 签名密钥的生成与使用。
 *
 * 上游 CLI 把 `signer generate` / `signer sign` 分作两步：先落一对密钥，再拿私钥去签产物。
 * 本模块自研这套能力，密钥格式与文件布局由实现决定，编排层只认「生成」与「签名」两件事。
 */
interface Signer {
    /**
     * 生成一对签名密钥。
     *
     * 目标文件已存在时覆写。
     *
     * @param privateKey 私钥落点
     * @param publicKey 公钥落点，为 null 时由实现按 [privateKey] 推导
     * @return 实际写出的私钥路径
     */
    fun generate(
        privateKey: Path,
        publicKey: Path? = null,
    ): Path

    /**
     * 用私钥给产物签名。
     *
     * @param artifact 待签名文件
     * @param privateKey 私钥路径
     * @param signature 签名落点
     * @return [signature]
     */
    fun sign(
        artifact: Path,
        privateKey: Path,
        signature: Path,
    ): Path
}
