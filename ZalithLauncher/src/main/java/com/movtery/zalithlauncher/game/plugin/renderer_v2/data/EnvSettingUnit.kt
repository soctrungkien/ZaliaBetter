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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.zalithlauncher.setting.unit.AbstractSettingUnit

/**
 * 渲染器可配置环境变量的设置单元（sealed）
 * @param title 该配置项的标题文本，由插件提供
 */
sealed class EnvSettingUnit(
    envKey: String,
    defaultValue: String,
    val title: String?,
    protected val storage: EnvStorageScope,
) : AbstractSettingUnit<String>(envKey, defaultValue) {

    override fun getValue(): String {
        return storage.getValue(key, defaultValue)
            .also { state = it }
    }

    override fun saveValue(v: String): String {
        storage.putValue(key, v)
        return v
    }

    /**
     * 选项式环境变量：从预设列表中选择一个值
     * @param rawEnv 原始环境变量配置
     * @param values 所有可选值（含默认值）
     */
    class Selectable(
        val rawEnv: RendererConfig.Env.SelectableEnv,
        defaultValue: String,
        val values: List<String>,
        title: String? = null,
        storage: EnvStorageScope,
    ) : EnvSettingUnit(rawEnv.key, defaultValue, title, storage) {
        /**
         * 当前是否启用此环境变量
         */
        var isEnabled by mutableStateOf(rawEnv.check != false)
            private set

        fun initCheck() {
            val pluginDefault = rawEnv.check != false

            isEnabled = if (rawEnv.check == null) {
                true
            } else {
                storage.getCheck(key, pluginDefault)
            }
        }

        fun saveCheck(enabled: Boolean) {
            isEnabled = enabled
            storage.putCheck(key, enabled)
        }
    }

    /**
     * 自由填写式环境变量：用户可自行输入任意值
     * @param rawEnv 原始环境变量配置
     */
    class Customizable(
        val rawEnv: RendererConfig.Env.CustomizableEnv,
        defaultValue: String,
        title: String? = null,
        storage: EnvStorageScope,
    ) : EnvSettingUnit(rawEnv.key, defaultValue, title, storage)

    /**
     * 开关式环境变量：启用/禁用该环境变量
     * 启用时环境变量值为 [RendererConfig.Env.ToggleableEnv.value]，禁用时不设置
     * @param rawEnv 原始环境变量配置
     * @param envValue 该环境变量的实际值（toggle=true 时使用）
     */
    class Toggleable(
        val rawEnv: RendererConfig.Env.ToggleableEnv,
        defaultValue: String,
        val envValue: String,
        title: String? = null,
        storage: EnvStorageScope,
    ) : EnvSettingUnit(rawEnv.key, defaultValue, title, storage) {
        /** 当前开关是否启用 */
        val isEnabled: Boolean get() = state.isNotEmpty()
    }
}
