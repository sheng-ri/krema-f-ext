/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api

import java.util.Locale

/**
 * Platform family a [BundleFormat] belongs to.
 *
 * Only the three families Krema packages for; any other host maps to [UNKNOWN] and produces no platform packages.
 */
enum class Platform {
    DARWIN,
    LINUX,
    WINDOWS,
    UNKNOWN,
    ;

    companion object {
        /** 将 JVM 的 os.name 映射到平台族。 */
        internal fun of(osName: String) =
            osName.lowercase(Locale.ROOT).let { name ->
                when {
                    "mac" in name || "darwin" in name -> DARWIN
                    "win" in name -> WINDOWS
                    "nux" in name || "nix" in name || "bsd" in name -> LINUX
                    else -> UNKNOWN
                }
            }
    }
}
