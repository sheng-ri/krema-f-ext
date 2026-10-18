/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api

import top.birthcat.kremaf.ext.gradle.internal.config.kremaPlatformTaskName

/**
 * A platform package format: the installable artifact native to one platform.
 *
 * The build script lists every format it wants; the plugin keeps only the host platform's subset,
 * so the same script runs on every machine. Tool selection follows [producer] rather than the
 * host platform: how an artifact is produced is a property of the format itself.
 */
enum class BundleFormat(
    /** Stable identifier used in output paths; renaming it moves the artifacts. */
    val id: String,
    /** Platform family this format belongs to. */
    val platform: Platform,
    /** Value passed to jpackage as --type; null for [Producer.APP_IMAGE_TOOL]. */
    val jpackageType: String?,
    /** How this format is produced. */
    val producer: Producer,
) {
    /** macOS application bundle; the app-image directory itself is the artifact. */
    APP("app", Platform.DARWIN, "app-image", Producer.APP_IMAGE),

    /** macOS disk image. */
    DMG("dmg", Platform.DARWIN, "dmg", Producer.JPACKAGE_INSTALLER),

    /** macOS installer package. */
    PKG("pkg", Platform.DARWIN, "pkg", Producer.JPACKAGE_INSTALLER),

    /** Windows installer executable. */
    EXE("exe", Platform.WINDOWS, "exe", Producer.JPACKAGE_INSTALLER),

    /** Windows installer package. */
    MSI("msi", Platform.WINDOWS, "msi", Producer.JPACKAGE_INSTALLER),

    /** Linux single-file executable, wrapped by appimagetool. */
    APP_IMAGE("appimage", Platform.LINUX, null, Producer.APP_IMAGE_TOOL),

    /** Debian package. */
    DEB("deb", Platform.LINUX, "deb", Producer.JPACKAGE_INSTALLER),

    /** RPM package. */
    RPM("rpm", Platform.LINUX, "rpm", Producer.JPACKAGE_INSTALLER),
    ;

    /** How an artifact is produced from the shared app-image. */
    enum class Producer {
        /** The app-image directory is the artifact (macOS .app is the application identity). */
        APP_IMAGE,

        /** jpackage installer that reuses the existing app-image. */
        JPACKAGE_INSTALLER,

        /** The app-image directory is wrapped by the external appimagetool. */
        APP_IMAGE_TOOL,
    }

    /** 注册平台包 task 时用的名字。 */
    internal val kremaPlatformTaskName get() = kremaPlatformTaskName(name)
}
