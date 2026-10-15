/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.tool

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/** Ed25519 密钥对生成与签名：JDK 内置无需 BouncyCastle，密钥恒定、适合塞进 `krema.toml` 的 `updater` 段。 */
class Ed25519Signer {
    fun generate(
        privateKey: Path,
        publicKey: Path? = null,
    ): Path {
        val pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair()
        writePem(privateKey, "PRIVATE KEY", pair.private.encoded)
        val pub = publicKey ?: privateKey.resolveSibling(privateKey.fileName.toString().removeSuffix(".key") + ".pub")
        writePem(pub, "PUBLIC KEY", pair.public.encoded)
        return privateKey
    }

    fun sign(
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
