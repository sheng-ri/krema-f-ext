/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api

/**
 * Names of the tasks registered by the plugin.
 *
 * Build scripts and CI can reference these constants instead of hard-coding strings.
 */
object KremaTaskNames {
    /** Task group every Krema task belongs to. */
    const val GROUP = "krema"

    /** Generates the project scaffold. */
    const val INIT = "kremaInit"

    /** Aggregates the app jar, the assets jar and the runtime dependency jars into lib/. */
    const val BUILD = "kremaBuild"

    /** Application jar: classes, resources and the Main-Class attribute. */
    const val APP_JAR = "kremaAppJar"

    /** Frontend assets jar; its contents live under the assets/ directory. */
    const val ASSETS_JAR = "kremaAssetsJar"

    /** Copies the runtime dependency jars next to the app and assets jars in lib/. */
    const val LIB = "kremaLib"

    /** Uncompressed, runnable app-image; the base for every platform package. */
    const val BUNDLE = "kremaBundle"

    /** Aggregates the platform package tasks configured for the host; succeeds when none are configured. */
    const val BUNDLE_PLATFORM = "kremaBundlePlatform"

    /** Runs the application from the classpath with KREMA_DEV_URL injected; long-lived. */
    const val DEV_RUN = "kremaDevRun"

    /** Runs the application from the packaged app-image; long-lived. */
    const val BUNDLE_RUN = "kremaBundleRun"

    /** Generates an Ed25519 signing key pair. */
    const val SIGNER_GENERATE = "kremaSignerGenerate"

    /** Signs an artifact with the private key. */
    const val SIGNER_SIGN = "kremaSignerSign"

    /** Runs the frontend production build and declares its output directory as a cacheable artifact. */
    const val FRONTEND_BUILD = "kremaFrontendBuild"

    /** Derives modules with jdeps and trims a minimal runtime image with jlink. */
    const val RUNTIME_IMAGE = "kremaRuntimeImage"

    /** Prints the runtime module list derived from jdeps. */
    const val JDEPS_MODULES = "jdepsModules"

    /**
     * Prints per-package jdeps -v details and writes a report.
     *
     * Named without the krema prefix because jdeps' -verbose and --print-module-deps are
     * mutually exclusive, so the two inspections cannot be one task with a switch.
     */
    const val JDEPS_MODULES_VERBOSE = "jdepsModulesVerbose"
}
