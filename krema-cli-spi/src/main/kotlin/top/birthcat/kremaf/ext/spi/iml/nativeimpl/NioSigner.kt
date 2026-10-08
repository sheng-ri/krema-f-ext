/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.nativeimpl

import top.birthcat.kremaf.ext.spi.port.Signer
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * [Signer] 的纯 JVM 实现。
 *
 * 用 Ed25519：JDK 15 起内置，无需 BouncyCastle，密钥长度恒定，适合整段塞进 `krema.toml` 的 `updater` 段。
 * 私钥、公钥都以 PKCS#8 / X.509 的 Base64 文本落盘，`----` 包装与上游的 PEM 观感一致。
 */
class NioSigner : Signer {
    override fun generate(
        privateKey: Path,
        publicKey: Path?,
    ): Path {
        val pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair()
        writePem(privateKey, "PRIVATE KEY", pair.private.encoded)
        val pub = publicKey ?: privateKey.resolveSibling(privateKey.fileName.toString().removeSuffix(".key") + ".pub")
        writePem(pub, "PUBLIC KEY", pair.public.encoded)
        return privateKey
    }

    override fun sign(
        artifact: Path,
        privateKey: Path,
        signature: Path,
    ): Path {
        val key =
            java.security.KeyFactory
                .getInstance(ALGORITHM)
                .generatePrivate(java.security.spec.PKCS8EncodedKeySpec(readPem(privateKey)))
        val signer = Signature.getInstance(ALGORITHM)
        signer.initSign(key)
        signer.update(Files.readAllBytes(artifact))
        writeBinary(signature, signer.sign())
        return signature
    }

    private fun writePem(
        path: Path,
        label: String,
        encoded: ByteArray,
    ) {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)
        write(path, "-----BEGIN $label-----\n$body\n-----END $label-----\n")
    }

    private fun readPem(path: Path): ByteArray =
        Files
            .readString(path)
            .lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
            .let(Base64.getDecoder()::decode)

    private fun write(
        path: Path,
        content: String,
    ) {
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, content)
    }

    private fun writeBinary(
        path: Path,
        bytes: ByteArray,
    ) {
        path.parent?.let(Files::createDirectories)
        Files.write(path, bytes)
    }

    private companion object {
        private const val ALGORITHM = "Ed25519"
    }
}
