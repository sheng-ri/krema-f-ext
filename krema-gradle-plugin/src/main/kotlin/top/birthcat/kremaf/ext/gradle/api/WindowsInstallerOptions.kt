/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import java.io.Serializable

/**
 * Windows installer options, forwarded one-to-one as jpackage `--win-*` flags.
 *
 * Only exe and msi installers accept them; the app-image and every other platform ignore this
 * type as a whole. A null boolean or string means the flag is omitted, leaving jpackage's own
 * default in place rather than the plugin inventing one.
 */
data class WindowsInstallerOptions(
    /** `--win-dir-chooser`: asks the user to choose the installation directory. */
    @get:Input
    @get:Optional
    val dirChooser: Boolean? = null,
    /** `--win-help-url`: page the user can visit for more information or support. */
    @get:Input
    @get:Optional
    val helpUrl: String? = null,
    /** `--win-menu`: adds a Start menu shortcut. */
    @get:Input
    @get:Optional
    val menu: Boolean? = null,
    /** `--win-menu-group`: Start menu group the shortcut belongs to. */
    @get:Input
    @get:Optional
    val menuGroup: String? = null,
    /** `--win-per-user-install`: installs for the current user instead of all users. */
    @get:Input
    @get:Optional
    val perUserInstall: Boolean? = null,
    /** `--win-shortcut`: adds a desktop shortcut. */
    @get:Input
    @get:Optional
    val shortcut: Boolean? = null,
    /** `--win-shortcut-prompt`: asks the user whether the installer should create shortcuts. */
    @get:Input
    @get:Optional
    val shortcutPrompt: Boolean? = null,
    /** `--win-update-url`: page carrying information about available updates. */
    @get:Input
    @get:Optional
    val updateUrl: String? = null,
    /** `--win-upgrade-uuid`: identity used to upgrade an earlier installation in place. */
    @get:Input
    @get:Optional
    val upgradeUuid: String? = null,
) : Serializable
