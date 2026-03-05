/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.version.installed.utils

import com.movtery.zalithlauncher.game.versioninfo.LEGACY_RELEASE_REGEX
import com.movtery.zalithlauncher.game.versioninfo.NewMinecraftVersion
import com.movtery.zalithlauncher.game.versioninfo.isRelease
import com.movtery.zalithlauncher.game.versioninfo.isSnapshot
import com.movtery.zalithlauncher.game.versioninfo.parseNewVersionFormat
import com.movtery.zalithlauncher.utils.string.isBiggerTo
import java.util.regex.Pattern

private val mLegacySnapshotRegex = Pattern.compile("^\\d+[a-zA-Z]\\d+[a-zA-Z]$")
private val mLegacyReleaseRegex = Pattern.compile(LEGACY_RELEASE_REGEX)

/**
 * 判断当前版本是否为旧版规则下的快照版本
 */
fun String.isLegacySnapshotVer(): Boolean = mLegacySnapshotRegex.matcher(this).matches()

/**
 * 判断当前版本是否为旧版规则下的正式版本
 */
fun String.isLegacyReleaseVer(): Boolean = mLegacyReleaseRegex.matcher(this).matches()

/**
 * 判断当前版本是否为快照版本
 */
fun String.isSnapshotVer(): Boolean {
    return this.isLegacySnapshotVer() ||
            (parseNewVersionFormat(this)?.isSnapshot() == true)
}

/**
 * 判断当前版本是否为正式版本
 */
fun String.isReleaseVer(): Boolean {
    return this.isLegacyReleaseVer() ||
            (parseNewVersionFormat(this)?.isRelease() == true)
}

/**
 * 判断是否为新版版本格式
 */
fun String.isNewVersionFormat(): Boolean =
    parseNewVersionFormat(this) != null



private fun String.determineVersionType(): VersionType {
    return when {
        parseNewVersionFormat(this)?.isSnapshot() == true -> VersionType.NEW_SNAPSHOT
        parseNewVersionFormat(this)?.isRelease() == true -> VersionType.NEW_RELEASE
        this.isLegacySnapshotVer() -> VersionType.LEGACY_SNAPSHOT
        this.isLegacyReleaseVer() -> VersionType.LEGACY_RELEASE
        else -> VersionType.UNKNOWN
    }
}

private enum class VersionType {
    LEGACY_SNAPSHOT,
    LEGACY_RELEASE,
    NEW_SNAPSHOT,
    NEW_RELEASE,
    UNKNOWN
}

private fun String.parseVersion(): ComparableVersion? {
    return when {
        this.isLegacySnapshotVer() || this.isLegacyReleaseVer() -> LegacyVersion(this)
        else -> parseNewVersionFormat(this)?.let { NewVersion(it) }
    }
}

private sealed class ComparableVersion : Comparable<ComparableVersion> {
    abstract val original: String

    companion object {
        fun compareVersions(v1: String, v2: String): Int {
            val c1 = v1.parseVersion()
            val c2 = v2.parseVersion()

            return when {
                c1 != null && c2 != null -> c1.compareTo(c2)

                v1.isNewVersionFormat() && (v2.isLegacySnapshotVer() || v2.isLegacyReleaseVer()) -> 1
                (v1.isLegacySnapshotVer() || v1.isLegacyReleaseVer()) && v2.isNewVersionFormat() -> -1

                else -> {
                    if (v1.isBiggerTo(v2)) 1
                    else if (v2.isBiggerTo(v1)) -1
                    else 0
                }
            }
        }
    }
}

/**
 * 旧版版本的包装
 */
private data class LegacyVersion(override val original: String) : ComparableVersion() {
    override fun compareTo(other: ComparableVersion): Int {
        return when (other) {
            is LegacyVersion -> {
                if (this.original.isBiggerTo(other.original)) 1
                else if (other.original.isBiggerTo(this.original)) -1
                else 0
            }
            is NewVersion -> -1
        }
    }
}

/**
 * 新版版本的包装
 */
private data class NewVersion(val version: NewMinecraftVersion) : ComparableVersion() {
    override val original: String = version.fullVersion

    override fun compareTo(other: ComparableVersion): Int {
        return when (other) {
            is LegacyVersion -> 1
            is NewVersion -> -version.compareTo(other.version)
        }
    }
}

/**
 * 判断版本是否大于某个版本
 * @param releaseVer 若判断该版本为正式版，则与它比较
 * @param snapshotVer 若判断该版本为快照版，则与它比较
 */
fun String.isBiggerVer(releaseVer: String, snapshotVer: String): Boolean {
    val ver = mapRealVer()
    val versionType = ver.determineVersionType()
    val targetVer = when (versionType) {
        VersionType.LEGACY_SNAPSHOT, VersionType.NEW_SNAPSHOT -> snapshotVer
        VersionType.LEGACY_RELEASE, VersionType.NEW_RELEASE -> releaseVer
        else -> {
            if (ver.isSnapshotVer()) snapshotVer else releaseVer
        }
    }

    return ComparableVersion.compareVersions(ver, targetVer) > 0
}

/**
 * 判断版本是否大于等于某个版本
 * @param releaseVer 若判断该版本为正式版，则与它比较
 * @param snapshotVer 若判断该版本为快照版，则与它比较
 */
fun String.isBiggerOrEqualVer(releaseVer: String, snapshotVer: String): Boolean {
    val ver = mapRealVer()
    val versionType = ver.determineVersionType()
    val targetVer = when (versionType) {
        VersionType.LEGACY_SNAPSHOT, VersionType.NEW_SNAPSHOT -> snapshotVer
        VersionType.LEGACY_RELEASE, VersionType.NEW_RELEASE -> releaseVer
        else -> {
            if (ver.isSnapshotVer()) snapshotVer else releaseVer
        }
    }

    return ComparableVersion.compareVersions(ver, targetVer) >= 0
}

/**
 * 判断版本是否小于某个版本
 * @param releaseVer 若判断该版本为正式版，则与它比较
 * @param snapshotVer 若判断该版本为快照版，则与它比较
 */
fun String.isLowerVer(releaseVer: String, snapshotVer: String): Boolean {
    val ver = mapRealVer()
    val versionType = ver.determineVersionType()
    val targetVer = when (versionType) {
        VersionType.LEGACY_SNAPSHOT, VersionType.NEW_SNAPSHOT -> snapshotVer
        VersionType.LEGACY_RELEASE, VersionType.NEW_RELEASE -> releaseVer
        else -> {
            if (ver.isSnapshotVer()) snapshotVer else releaseVer
        }
    }

    return ComparableVersion.compareVersions(ver, targetVer) < 0
}

/**
 * 判断版本是否小于等于某个版本
 * @param releaseVer 若判断该版本为正式版，则与它比较
 * @param snapshotVer 若判断该版本为快照版，则与它比较
 */
fun String.isLowerOrEqualVer(releaseVer: String, snapshotVer: String): Boolean {
    val ver = mapRealVer()
    val versionType = ver.determineVersionType()
    val targetVer = when (versionType) {
        VersionType.LEGACY_SNAPSHOT, VersionType.NEW_SNAPSHOT -> snapshotVer
        VersionType.LEGACY_RELEASE, VersionType.NEW_RELEASE -> releaseVer
        else -> {
            if (ver.isSnapshotVer()) snapshotVer else releaseVer
        }
    }

    return ComparableVersion.compareVersions(ver, targetVer) <= 0
}

private fun String.mapRealVer(): String {
    return if (this.startsWith("2.0_")) {
        "1.5.1" //愚人节版本2.0，实际版本为1.5.1
    } else this
}