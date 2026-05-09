package com.movtery.zalithlauncher.upgrade

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.movtery.zalithlauncher.utils.device.Architecture

/**
 * Remote update data (BACKWARD + GITHUB COMPATIBLE)
 */
@Serializable
data class RemoteData(
    @SerialName("code")
    val code: Int,

    @SerialName("version")
    val version: String,

    @SerialName("created_at")
    val createdAt: String,

    // =========================
    // OLD SYSTEM (KEEP SAFE)
    // =========================

    @SerialName("default_cloud_drive")
    val defaultCloudDrive: CloudDrive? = null,

    @SerialName("cloud_drives")
    val cloudDrives: List<CloudDrive> = emptyList(),

    @SerialName("files")
    val files: List<RemoteFile> = emptyList(),

    @SerialName("default_body")
    val defaultBody: RemoteBody? = null,

    @SerialName("bodies")
    val bodies: List<RemoteBody> = emptyList(),

    // =========================
    // NEW OPTIONAL GITHUB FIELD
    // (NOT BREAK OLD JSON)
    // =========================

    @SerialName("tag_name")
    val tagName: String? = null,

    @SerialName("assets")
    val assets: List<GithubAsset>? = null
) {

    // =========================================================
    // 🔥 COMPATIBILITY LAYER (AUTO APK SELECTOR)
    // =========================================================

    /**
     * Lấy APK phù hợp (ưu tiên GitHub assets nếu có)
     */
    fun getCompatibleApkUrl(): String? {

        // 1. PRIORITY: GitHub Releases format
        assets?.let { list ->
            val arch = getDeviceArchTag()

            return list.firstOrNull {
                it.name.contains(arch, ignoreCase = true)
            }?.downloadUrl
                ?: list.firstOrNull { it.name.contains("universal", true) }
                ?.downloadUrl
        }

        // 2. FALLBACK: old system (RemoteFile)
        val file = files.firstOrNull {
            it.arch == RemoteFile.Arch.ALL ||
                    it.arch.name.equals(getDeviceArchEnum().name, true)
        }

        return file?.uri
    }

    /**
     * Device arch → string (GitHub filename style)
     */
    private fun getDeviceArchTag(): String {
        return when (Architecture.getDeviceArchitecture()) {
            Architecture.ARM64 -> "arm64-v8a"
            Architecture.ARM -> "armeabi-v7a"
            Architecture.X86 -> "x86"
            Architecture.X86_64 -> "x86_64"
            else -> "universal"
        }
    }

    /**
     * Device arch → enum (old system)
     */
    private fun getDeviceArchEnum(): RemoteFile.Arch {
        return when (Architecture.getDeviceArchitecture()) {
            Architecture.ARM64 -> RemoteFile.Arch.ARM64
            Architecture.ARM -> RemoteFile.Arch.ARM
            Architecture.X86 -> RemoteFile.Arch.X86
            Architecture.X86_64 -> RemoteFile.Arch.X86_64
            else -> RemoteFile.Arch.ALL
        }
    }

    // =========================================================
    // OLD MODELS (UNCHANGED)
    // =========================================================

    @Serializable
    data class CloudDrive(
        @SerialName("language")
        val language: String,
        @SerialName("link")
        val link: String,
        @SerialName("links")
        val links: List<Link> = emptyList()
    ) {
        @Serializable
        data class Link(
            @SerialName("name")
            val name: String,
            @SerialName("link")
            val link: String
        )
    }

    @Serializable
    data class RemoteFile(
        @SerialName("file_name")
        val fileName: String,
        @SerialName("uri")
        val uri: String,
        @SerialName("arch")
        val arch: Arch,
        @SerialName("size")
        val size: Long = 0L
    ) {
        @Serializable
        enum class Arch {
            @SerialName("all") ALL,
            @SerialName("arm") ARM,
            @SerialName("arm64") ARM64,
            @SerialName("x86") X86,
            @SerialName("x86_64") X86_64
        }
    }

    @Serializable
    data class RemoteBody(
        @SerialName("language")
        val language: String,
        @SerialName("markdown")
        val markdown: String
    )

    // =========================================================
    // GITHUB MODEL (OPTIONAL)
    // =========================================================

    @Serializable
    data class GithubAsset(
        val name: String,
        @SerialName("browser_download_url")
        val downloadUrl: String
    )
}
