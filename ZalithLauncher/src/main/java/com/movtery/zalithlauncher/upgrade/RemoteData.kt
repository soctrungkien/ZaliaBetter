package com.movtery.zalithlauncher.upgrade

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.movtery.zalithlauncher.utils.device.Architecture

@Serializable
data class RemoteData(

    @SerialName("code")
    val code: Int = 0,

    @SerialName("version")
    val version: String = "",

    @SerialName("created_at")
    val createdAt: String = "",

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

    // GitHub release fields

    @SerialName("tag_name")
    val tagName: String? = null,

    @SerialName("name")
    val title: String? = null,

    @SerialName("body")
    val githubBody: String? = null,

    @SerialName("html_url")
    val htmlUrl: String? = null,

    @SerialName("assets")
    val assets: List<GithubAsset>? = null
) {

    fun hasGithubRelease(): Boolean {
        return !tagName.isNullOrBlank()
    }

    fun getVersionName(): String {
        return tagName ?: version
    }

    fun getReleaseTitle(): String {
        return title ?: getVersionName()
    }

    fun getReleaseBody(): String {
        return githubBody
            ?: defaultBody?.markdown
            ?: ""
    }

    fun getReleasePage(): String {
        return htmlUrl ?: URL_RELEASE_PAGE
    }

    fun getCompatibleApkUrl(): String? {

        assets?.let { list ->

            val tags = getCompatibleTags()

            for (tag in tags) {

                val apk = list.firstOrNull {
                    it.name.endsWith(".apk", true) &&
                    it.name.contains(tag, true) &&
                    !it.name.contains("debug", true)
                }

                if (apk != null) {
                    return apk.downloadUrl
                }
            }

            return list.firstOrNull {
                it.name.endsWith(".apk", true)
            }?.downloadUrl
        }

        val arch = getDeviceArchEnum()

        return files.firstOrNull {
            it.arch == RemoteFile.Arch.ALL ||
                    it.arch == arch
        }?.uri
    }

    private fun getCompatibleTags(): List<String> {
        return when (Architecture.getDeviceArchitecture()) {

            Architecture.ARM64 ->
                listOf("arm64-v8a", "armeabi-v7a", "universal", "all")

            Architecture.ARM ->
                listOf("armeabi-v7a", "universal", "all")

            Architecture.X86_64 ->
                listOf("x86_64", "x86", "universal", "all")

            Architecture.X86 ->
                listOf("x86", "universal", "all")

            else ->
                listOf("universal", "all")
        }
    }

    private fun getDeviceArchEnum(): RemoteFile.Arch {
        return when (Architecture.getDeviceArchitecture()) {
            Architecture.ARM64 -> RemoteFile.Arch.ARM64
            Architecture.ARM -> RemoteFile.Arch.ARM
            Architecture.X86 -> RemoteFile.Arch.X86
            Architecture.X86_64 -> RemoteFile.Arch.X86_64
            else -> RemoteFile.Arch.ALL
        }
    }

    @Serializable
    data class GithubAsset(
        val name: String,

        @SerialName("browser_download_url")
        val downloadUrl: String
    )

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
}
