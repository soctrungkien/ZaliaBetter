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

package com.movtery.zalithlauncher.game.plugin.renderer_v2.data

import com.movtery.zalithlauncher.game.version.installed.getZalithVersionPath
import com.movtery.zalithlauncher.path.GLOBAL_JSON
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File
import java.io.FileOutputStream

private const val TAG = "EnvStorage"

/**
 * 环境变量存储接口，支持全局存储（MMKV）与版本独立存储（renderer.config）
 */
interface EnvStorage {
    /**
     * 获取指定渲染器插件包名下的命名空间存储
     */
    fun scoped(packageName: String): EnvStorageScope
}

/**
 * 渲染器插件命名空间内的环境变量存储
 */
interface EnvStorageScope {
    fun getValue(key: String, defaultValue: String): String
    fun putValue(key: String, value: String)
    fun getCheck(key: String, defaultValue: Boolean): Boolean
    fun putCheck(key: String, value: Boolean)
    fun remove(key: String)
    fun keys(): Set<String>
}

/**
 * 全局环境变量存储（基于 MMKV）
 */
object GlobalEnvStorage : EnvStorage {
    override fun scoped(packageName: String): EnvStorageScope = Scope(packageName)

    private class Scope(
        private val packageName: String
    ) : EnvStorageScope {
        private val mmkv get() = rendererEnvMMKV()

        override fun getValue(key: String, defaultValue: String): String =
            mmkv.getString(valueKey(key), defaultValue) ?: defaultValue

        override fun putValue(key: String, value: String) {
            mmkv.putString(valueKey(key), value).apply()
        }

        override fun getCheck(key: String, defaultValue: Boolean): Boolean =
            mmkv.getBoolean(checkKey(key), defaultValue)

        override fun putCheck(key: String, value: Boolean) {
            mmkv.putBoolean(checkKey(key), value).apply()
        }

        override fun remove(key: String) {
            mmkv.remove(valueKey(key))
            mmkv.remove(checkKey(key))
        }

        override fun keys(): Set<String> {
            val prefix = "$packageName:"
            return mmkv.allKeys()
                ?.filter { it.startsWith(prefix) }
                ?.map { it.removePrefix(prefix).removeSuffix(":check") }
                ?.toSet()
                ?: emptySet()
        }

        private fun valueKey(key: String) = "$packageName:$key"
        private fun checkKey(key: String) = "$packageName:$key:check"
    }
}

/**
 * 版本独立环境变量存储
 * 读写均只针对该版本的独立配置文件，不继承全局配置
 */
class VersionEnvStorage(
    versionFolder: File
) : EnvStorage {
    private val configFolder = getZalithVersionPath(versionFolder)
    private val configFile = File(configFolder, RendererEnvConfig.CONFIG_FILE_NAME)
    private var config: RendererEnvConfig = loadConfig()

    override fun scoped(packageName: String): EnvStorageScope = Scope(packageName)

    /**
     * 删除该版本的全部独立环境变量配置
     */
    fun delete() {
        runCatching {
            if (configFile.exists() && !configFile.delete()) {
                error("Failed to delete the renderer environment configuration.")
            }
            config = RendererEnvConfig()
        }.onFailure { e ->
            Logger.error(TAG, "An exception occurred while deleting the renderer environment configuration.", e)
        }
    }

    private inner class Scope(
        private val packageName: String
    ) : EnvStorageScope {
        override fun getValue(key: String, defaultValue: String): String =
            config.renderers[packageName]?.envs?.get(key) ?: defaultValue

        override fun putValue(key: String, value: String) {
            envs().envs[key] = value
            persist()
        }

        override fun getCheck(key: String, defaultValue: Boolean): Boolean =
            config.renderers[packageName]?.checks?.get(key) ?: defaultValue

        override fun putCheck(key: String, value: Boolean) {
            envs().checks[key] = value
            persist()
        }

        override fun remove(key: String) {
            val entry = config.renderers[packageName] ?: return
            val changed = entry.envs.remove(key) != null || entry.checks.remove(key) != null
            if (changed) {
                if (entry.envs.isEmpty() && entry.checks.isEmpty()) {
                    config.renderers.remove(packageName)
                }
                persist()
            }
        }

        override fun keys(): Set<String> {
            val entry = config.renderers[packageName] ?: return emptySet()
            return entry.envs.keys + entry.checks.keys
        }

        private fun envs(): RendererEnvs = config.renderers.getOrPut(packageName) { RendererEnvs() }
    }

    private fun loadConfig(): RendererEnvConfig {
        if (!configFile.isFile) return RendererEnvConfig()
        return runCatching {
            GLOBAL_JSON.decodeFromString<RendererEnvConfig>(configFile.readText())
        }.onFailure { e ->
            Logger.error(TAG, "Failed to parse the renderer environment configuration.", e)
        }.getOrNull() ?: RendererEnvConfig()
    }

    private fun persist() {
        val tmpFile = File(configFolder, "${RendererEnvConfig.CONFIG_FILE_NAME}.tmp")
        runCatching {
            if (!configFolder.exists()) configFolder.mkdirs()
            FileOutputStream(tmpFile).use { stream ->
                stream.write(GLOBAL_JSON.encodeToString(config).toByteArray())
                stream.fd.sync()
            }
            check(tmpFile.renameTo(configFile)) { "Failed to replace the renderer environment configuration." }
        }.onFailure { e ->
            tmpFile.delete()
            Logger.error(TAG, "An exception occurred while saving the renderer environment configuration.", e)
        }
    }
}
