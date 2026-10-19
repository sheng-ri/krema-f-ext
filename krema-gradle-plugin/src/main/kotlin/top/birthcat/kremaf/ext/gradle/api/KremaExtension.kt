/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Internal
import top.birthcat.kremaf.ext.gradle.internal.domain.PlatformDetector

/**
 * The "krema { }" extension: the only place to override build inputs.
 *
 * Everything that is not Gradle-specific lives in krema.toml. Where a key exists in both places
 * the value here wins; required keys are never given placeholder defaults, so a missing value
 * fails where it is read instead of silently producing an artifact under someone else's name.
 */
abstract class KremaExtension {
    /** Location of krema.toml; defaults to krema.toml in the project directory. */
    abstract val configFile: RegularFileProperty

    /** Main class, overriding build.main_class. */
    abstract val mainClass: Property<String>

    /**
     * Dev server URL, injected as KREMA_DEV_URL into the process started by kremaDevRun.
     *
     * Overrides build.frontend_dev_url (build.dev_url is also accepted) and must be present in
     * one of the two places: guessing a port is worse than failing, because the frontend
     * toolchain may listen somewhere else.
     */
    abstract val devUrl: Property<String>

    /** Frontend build output directory, overriding build.frontend_out_dir and build.out_dir. */
    abstract val frontendOutDir: DirectoryProperty

    /** Frontend inputs, wired to the "frontend { }" block when the plugin is applied. */
    @Internal
    lateinit var frontend: KremaFrontendExtension

    /** Platform package inputs, wired to the "bundling { }" block when the plugin is applied. */
    @Internal
    lateinit var bundling: KremaBundlingExtension

    /** Runtime image inputs, wired to the "runtime { }" block when the plugin is applied. */
    @Internal
    lateinit var runtime: KremaRuntimeExtension
}

/**
 * "krema.frontend { }": frontend project inputs.
 *
 * Property names map one-to-one to the frontend_* keys in krema.toml; the block name already
 * carries the prefix, hence [buildCommand] rather than frontendBuildCommand.
 */
abstract class KremaFrontendExtension {
    /**
     * Frontend project directory, relative to the project root; defaults to the project root.
     *
     * A plain string, not a [DirectoryProperty], because it accepts the same literal as the TOML
     * key. Resolving it to a directory needs layout, which only exists at registration time.
     */
    abstract val dir: Property<String>

    /** Production build command, e.g. "npm run build"; overrides build.frontend_command. */
    abstract val buildCommand: Property<String>

    /**
     * Dependency install command, e.g. "pnpm install".
     *
     * Only the user knows which package manager the project uses, so nothing is guessed. When
     * absent no install runs and node_modules must already be in place.
     */
    abstract val installCommand: Property<String>
}

/**
 * "krema.runtime { }": inputs for the trimmed runtime image.
 *
 * Without an explicit module list, jdeps derives it from the application classpath.
 */
abstract class KremaRuntimeExtension {
    /**
     * JDK modules to keep in the runtime image, e.g. "java.base".
     *
     * A non-empty set replaces jdeps inference rather than extending it: appending would quietly
     * add back modules the user removed and make the final image impossible to read off the list.
     */
    abstract val modules: SetProperty<String>
}

/**
 * "krema.bundling { }": which platform packages to build.
 *
 * Each platform has its own property; formats that do not match the host are ignored as a whole,
 * so one script works on every machine without per-machine switches.
 */
abstract class KremaBundlingExtension {
    /** Formats to build on macOS (.app, .dmg, .pkg); empty means no packages. */
    abstract val macosFormats: SetProperty<BundleFormat>

    /** Formats to build on Windows (.exe, .msi); empty means no packages. */
    abstract val windowsFormats: SetProperty<BundleFormat>

    /** Formats to build on Linux (.AppImage, .deb, .rpm); empty means no packages. */
    abstract val linuxFormats: SetProperty<BundleFormat>

    /**
     * macOS icon (icns). An unset or missing file skips --icon instead of failing the build.
     *
     * When all three icons are unset, bundle.icon from krema.toml is used as the fallback.
     */
    abstract val macIcon: RegularFileProperty

    /** Windows icon (ico); see [macIcon] for the fallback rules. */
    abstract val winIcon: RegularFileProperty

    /** Linux icon (png); see [macIcon] for the fallback rules. */
    abstract val linuxIcon: RegularFileProperty

    /**
     * Whether to download appimagetool into the build directory when it is not on PATH.
     *
     * Only affects the AppImage path; other formats go through jpackage. A failed download is
     * an error with both remedies spelled out, never a silent success.
     */
    abstract val appImageToolAutoDownload: Property<Boolean>

    /** Windows installer flags, wired to the "windows { }" block when the plugin is applied. */
    @Internal
    lateinit var windows: KremaWindowsExtension

    /**
     * Opens the nested "windows { }" block.
     *
     * Kotlin DSL only generates accessors for project-level extensions, so a nested block needs
     * this method to be callable at all; the property stays the storage that task wiring reads.
     */
    fun windows(configure: Action<KremaWindowsExtension>) = configure.execute(windows)

    /** 宿主平台要构建的格式；再按 platform 过滤会吞掉属性放错的格式。 */
    internal fun formatsForHost() =
        selectHostFormats(PlatformDetector.detect(), macosFormats.get(), windowsFormats.get(), linuxFormats.get())
}

/**
 * "krema.bundling.windows { }": Windows installer flags, overriding bundle.windows in krema.toml.
 *
 * A property set here wins over the same key in the TOML table; an explicit false wins too,
 * because jpackage has no third state for these flags — false simply means "do not pass it".
 */
abstract class KremaWindowsExtension {
    /** `--win-dir-chooser`; overrides dir_chooser, which is on unless disabled. */
    abstract val dirChooser: Property<Boolean>

    /** `--win-help-url`; overrides help_url. */
    abstract val helpUrl: Property<String>

    /** `--win-menu`; overrides menu. */
    abstract val menu: Property<Boolean>

    /** `--win-menu-group`; overrides menu_group. */
    abstract val menuGroup: Property<String>

    /** `--win-per-user-install`; overrides per_user_install. */
    abstract val perUserInstall: Property<Boolean>

    /** `--win-shortcut`; overrides shortcut, which is on unless disabled. */
    abstract val shortcut: Property<Boolean>

    /** `--win-shortcut-prompt`; overrides shortcut_prompt, which is on unless disabled. */
    abstract val shortcutPrompt: Property<Boolean>

    /** `--win-update-url`; overrides update_url. */
    abstract val updateUrl: Property<String>

    /** `--win-upgrade-uuid`; overrides upgrade_uuid. */
    abstract val upgradeUuid: Property<String>
}

/** 按宿主平台筛选格式，与 Gradle 的 SetProperty 解耦以便单测。 */
internal fun selectHostFormats(
    platform: Platform,
    macos: Set<BundleFormat>,
    windows: Set<BundleFormat>,
    linux: Set<BundleFormat>,
) = when (platform) {
    Platform.DARWIN -> macos
    Platform.WINDOWS -> windows
    Platform.LINUX -> linux
    Platform.UNKNOWN -> emptySet()
}
