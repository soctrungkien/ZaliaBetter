package com.movtery.zalithlauncher.upgrade

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.movtery.zalithlauncher.path.URL_RELEASE_PAGE
import com.movtery.zalithlauncher.utils.device.Architecture

/**
 * Remote update data
 */
@Serializable
data class RemoteData(
    @SerialName("code")
    val code: Int = 0,

    @SerialName("version")
    val version: String = "",

    @SerialName("created_at")
    val createdAt: String = "",

    // =========================
    // OLD SYSTEM
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
    // GITHUB RELEASE
    // =========================

    @SerialName("tag_name")
    val tagName: String? = null,

    @SerialName("body")
    val githubBody: String? = null,

    @SerialName("html_url")
    val htmlUrl: String? = null,

    @SerialName("assets")
    val assets: List<GithubAsset>? = null
) {

    /**
     * Latest version string
     */
    fun getLatestVersion(): String {
        return tagName ?: version
    }

    /**
     * Release page url
     */
    fun getReleasePage(): String {
        return htmlUrl ?: URL_RELEASE_PAGE
    }

    /**
     * Release body
     */
    fun getReleaseBody(): String {
        return githubBody
            ?: defaultBody?.markdown
            ?: ""
    }

    /**
     * Compatible apk url
     */
    fun getCompatibleApkUrl(): String? {

        // GitHub assets priority
        assets?.let { list ->
            val arch = getDeviceArchTag()

            return list.firstOrNull {
                it.name.contains(arch, ignoreCase = true)
            }?.downloadUrl
                ?: list.firstOrNull {
                    it.name.contains("universal", ignoreCase = true)
                }?.downloadUrl
                ?: list.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                }?.downloadUrl
        }

        // Old update system fallback
        val file = files.firstOrNull {
            it.arch == RemoteFile.Arch.ALL ||
                    it.arch == getDeviceArchEnum()
        }

        return file?.uri
    }

    /**
     * Device architecture string
     */
    private fun getDeviceArchTag(): String {
        return when (Architecture.getDeviceArchitecture()) {
            Architecture.ARCH_ARM64 -> "arm64-v8a"
            Architecture.ARCH_ARM -> "armeabi-v7a"
            Architecture.ARCH_X86 -> "x86"
            Architecture.ARCH_X86_64 -> "x86_64"
            else -> "universal"
        }
    }

    /**
     * Device architecture enum
     */
    private fun getDeviceArchEnum(): RemoteFile.Arch {
        return when (Architecture.getDeviceArchitecture()) {
            Architecture.ARCH_ARM64 -> RemoteFile.Arch.ARM64
            Architecture.ARCH_ARM -> RemoteFile.Arch.ARM
            Architecture.ARCH_X86 -> RemoteFile.Arch.X86
            Architecture.ARCH_X86_64 -> RemoteFile.Arch.X86_64
            else -> RemoteFile.Arch.ALL
        }
    }

    // =========================================================
    // OLD MODELS
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
            @SerialName("all")
            ALL,

            @SerialName("arm")
            ARM,

            @SerialName("arm64")
            ARM64,

            @SerialName("x86")
            X86,

            @SerialName("x86_64")
            X86_64
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
    // GITHUB ASSET
    // =========================================================

    @Serializable
    data class GithubAsset(
        @SerialName("name")
        val name: String,

        @SerialName("browser_download_url")
        val downloadUrl: String
    )
}
