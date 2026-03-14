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

package com.movtery.zalithlauncher.ui.screens.content.versions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.addons.modloader.ModLoader
import com.movtery.zalithlauncher.game.addons.modloader.cleanroom.CleanroomVersions
import com.movtery.zalithlauncher.game.addons.modloader.fabriclike.fabric.FabricVersions
import com.movtery.zalithlauncher.game.addons.modloader.fabriclike.quilt.QuiltVersions
import com.movtery.zalithlauncher.game.addons.modloader.forgelike.forge.ForgeVersions
import com.movtery.zalithlauncher.game.addons.modloader.forgelike.neoforge.NeoForgeVersions
import com.movtery.zalithlauncher.game.download.game.GameDownloadInfo
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionInfo
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.AnimatedLazyColumn
import com.movtery.zalithlauncher.ui.screens.NestedNavKey
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.download.game.AddonList
import com.movtery.zalithlauncher.ui.screens.content.download.game.CleanroomList
import com.movtery.zalithlauncher.ui.screens.content.download.game.CurrentAddon
import com.movtery.zalithlauncher.ui.screens.content.download.game.FabricList
import com.movtery.zalithlauncher.ui.screens.content.download.game.ForgeList
import com.movtery.zalithlauncher.ui.screens.content.download.game.LoaderVerSupports
import com.movtery.zalithlauncher.ui.screens.content.download.game.NeoForgeList
import com.movtery.zalithlauncher.ui.screens.content.download.game.QuiltList
import com.movtery.zalithlauncher.ui.screens.content.download.game.rememberLoaderVerSupports
import com.movtery.zalithlauncher.ui.screens.content.download.game.runWithState
import com.movtery.zalithlauncher.utils.logging.Logger.lInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AddonDiffs(
    val list: List<Diff>
) {
    sealed interface Diff {
        fun getLoader(): ModLoader
    }

    /**
     * 差异: 模组加载器版本
     * @param original 当前版本使用的版本
     * @param updateTo 要变更的版本
     */
    data class VersionChangeDiff(
        val modloader: ModLoader,
        val original: String,
        val updateTo: String
    ): Diff {
        override fun getLoader(): ModLoader = modloader
    }

    /**
     * 差异: 移除模组加载器
     */
    data class RemoveDiff(
        val modloader: ModLoader
    ): Diff {
        override fun getLoader(): ModLoader = modloader
    }

    /**
     * 差异: 载入新模组加载器
     * @param version 要安装的版本
     */
    data class NewLoadDiff(
        val modloader: ModLoader,
        val version: String
    ): Diff {
        override fun getLoader(): ModLoader = modloader
    }
}

/**
 * 生成模组加载器版本差异信息
 */
private fun CurrentAddon.generateDiff(
    loaderInfo: VersionInfo.LoaderInfo
): AddonDiffs {
    val currentLoader = loaderInfo.loader
    val currentVersion = loaderInfo.version

    val loaderVersions = mapOf(
        ModLoader.FORGE to forgeVersion.value,
        ModLoader.NEOFORGE to neoforgeVersion.value,
        ModLoader.FABRIC to fabricVersion.value,
        ModLoader.QUILT to quiltVersion.value,
        ModLoader.CLEANROOM to cleanroomVersion.value
    )

    val currentAddonVersion = if (loaderVersions.containsKey(currentLoader)) {
        loaderVersions[currentLoader]
    } else {
        error("The launcher does not support automatically downloading this loader: ${currentLoader.displayName}")
    }

    val diffs = mutableListOf<AddonDiffs.Diff>()

    //当前加载器的变更
    if (currentAddonVersion == null) {
        diffs.add(AddonDiffs.RemoveDiff(modloader = currentLoader))
    } else {
        diffs.add(
            AddonDiffs.VersionChangeDiff(
                modloader = currentLoader,
                original = currentVersion,
                updateTo = currentAddonVersion.getAddonVersion()
            )
        )
    }

    //新载入加载器的变更情况
    loaderVersions
        .filter { (loader, version) -> loader != currentLoader && version != null }
        .forEach { (loader, addonVersion) ->
            diffs.add(
                AddonDiffs.NewLoadDiff(
                    modloader = loader,
                    version = addonVersion!!.getAddonVersion()
                )
            )
        }

    return AddonDiffs(list = diffs.toList())
}

private class AddonsViewModel(
    private val gameVersion: String,
    private val loaderInfo: VersionInfo.LoaderInfo,
    private val loaderSupports: LoaderVerSupports
) : ViewModel() {
    private val mutex = Mutex()

    val addonList = AddonList()
    val currentAddon = CurrentAddon()

    /** 是否已经找到游戏使用的模组加载器版本 */
    private var isLoaderVersionFound: Boolean = false
    /** 所有加载器是否都已经完成初始化 */
    var isLoaded by mutableStateOf(false)
        private set
    /** 是否允许用户点击更新按钮 */
    var canUpdate by mutableStateOf(false)
        private set

    private suspend fun updateLoadedState() {
        mutex.withLock {
            if (isLoaded) return@withLock
            //已经找到游戏使用的模组加载器，或者所有的模组加载器列表都加载完成
            val isLoaded0 = isLoaderVersionFound || buildList {
                add(addonList.forgeList)
                if (loaderSupports.isNeoForgeSupports) {
                    add(addonList.neoforgeList)
                }
                if (loaderSupports.isFabricSupports) {
                    add(addonList.fabricList)
                }
                if (loaderSupports.isQuiltSupports) {
                    add(addonList.quiltList)
                }
                if (loaderSupports.isCleanroomSupports) {
                    add(addonList.cleanroomList)
                }
            }.all { it != null }
            if (isLoaded0) lInfo("Game’s mod loader found, or all mod loaders loaded.")
            isLoaded = isLoaded0
        }
    }

    fun checkCanUpdate() {
        if (!isLoaded) {
            //初始化未完成
            canUpdate = false
            return
        }

        val unselectedLoader = buildList {
            add(currentAddon.forgeVersion)
            if (loaderSupports.isNeoForgeSupports) {
                add(addonList.neoforgeList)
            }
            if (loaderSupports.isFabricSupports) {
                add(addonList.fabricList)
            }
            if (loaderSupports.isQuiltSupports) {
                add(addonList.quiltList)
            }
            if (loaderSupports.isCleanroomSupports) {
                add(addonList.cleanroomList)
            }
        }.isEmpty()
        if (unselectedLoader) {
            //用户没有选择任何加载器
            canUpdate = false
            return
        }

        val version = when (loaderInfo.loader) {
            ModLoader.FORGE -> currentAddon.forgeVersion.value
            ModLoader.NEOFORGE -> currentAddon.neoforgeVersion.value
            ModLoader.FABRIC -> currentAddon.fabricVersion.value
            ModLoader.QUILT -> currentAddon.quiltVersion.value
            ModLoader.CLEANROOM -> currentAddon.cleanroomVersion.value
            else -> null
        }

        if (version == null) {
            //如果加载器对应版本为空，则说明用户更改了游戏模组加载器，允许更新
            canUpdate = true
            return
        }

        //对比模组加载器与游戏的模组加载器的版本
        //能否更新取决于是否有做修改
        canUpdate = !version.isVersion(loaderInfo.version)
    }

    fun reloadForge() {
        reloadSingleAndCheck {
            reloadForgeAsync()
        }
    }

    private suspend fun reloadForgeAsync() = runWithState(
        { currentAddon.forgeState = it },
        { ForgeVersions.fetchForgeList(gameVersion) }
    ).also { versions ->
        addonList.forgeList = versions
        if (loaderInfo.loader == ModLoader.FORGE && currentAddon.forgeVersion.value == null) {
            currentAddon.forgeVersion.value = versions?.find {
                it.isVersion(loaderInfo.version)
            }?.also { isLoaderVersionFound = true }
        }
    }

    fun reloadNeoForge() {
        reloadSingleAndCheck {
            reloadNeoForgeAsync()
        }
    }

    private suspend fun reloadNeoForgeAsync() = runWithState(
        { currentAddon.neoforgeState = it },
        { NeoForgeVersions.fetchNeoForgeList(gameVersion = gameVersion) }
    ).also { versions ->
        addonList.neoforgeList = versions
        if (loaderInfo.loader == ModLoader.NEOFORGE && currentAddon.neoforgeVersion.value == null) {
            currentAddon.neoforgeVersion.value = versions?.find {
                it.isVersion(loaderInfo.version)
            }?.also { isLoaderVersionFound = true }
        }
    }

    fun reloadFabric() {
        reloadSingleAndCheck {
            reloadFabricAsync()
        }
    }

    private suspend fun reloadFabricAsync() = runWithState(
        { currentAddon.fabricState = it },
        { FabricVersions.fetchFabricLoaderList(gameVersion) }
    ).also { versions ->
        addonList.fabricList = versions
        if (loaderInfo.loader == ModLoader.FABRIC && currentAddon.fabricVersion.value == null) {
            currentAddon.fabricVersion.value = versions?.find {
                it.isVersion(loaderInfo.version)
            }?.also { isLoaderVersionFound = true }
        }
    }

    fun reloadQuilt() {
        reloadSingleAndCheck {
            reloadQuiltAsync()
        }
    }

    private suspend fun reloadQuiltAsync() = runWithState(
        { currentAddon.quiltState = it },
        { QuiltVersions.fetchQuiltLoaderList(gameVersion) }
    ).also { versions ->
        addonList.quiltList = versions
        if (loaderInfo.loader == ModLoader.QUILT && currentAddon.quiltVersion.value == null) {
            currentAddon.quiltVersion.value = versions?.find {
                it.isVersion(loaderInfo.version)
            }?.also { isLoaderVersionFound = true }
        }
    }


    fun reloadCleanroom() {
        reloadSingleAndCheck {
            reloadCleanroomAsync()
        }
    }

    private suspend fun reloadCleanroomAsync() = runWithState(
        { currentAddon.cleanroomState = it },
        { CleanroomVersions.fetchLoaderList(gameVersion) }
    ).also { versions ->
        addonList.cleanroomList = versions
        if (loaderInfo.loader == ModLoader.CLEANROOM && currentAddon.cleanroomVersion.value == null) {
            currentAddon.cleanroomVersion.value = versions?.find {
                it.isVersion(loaderInfo.version)
            }?.also { isLoaderVersionFound = true }
        }
    }

    /**
     * 后续如果有加载失败希望重载的列表时，使用这个函数进行单独重载。
     * 重新检查并应用加载成功的状态
     */
    private fun reloadSingleAndCheck(
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            block()
            updateLoadedState()
        }
    }

    /**
     * 一次性加载全部的模组加载器版本列表
     */
    private fun reloadAllLoaders() {
        viewModelScope.launch {
            buildList {
                add(async {
                    reloadForgeAsync()
                    updateLoadedState()
                })
                if (loaderSupports.isNeoForgeSupports) {
                    add(async {
                        reloadNeoForgeAsync()
                        updateLoadedState()
                    })
                }
                if (loaderSupports.isFabricSupports) {
                    add(async {
                        reloadFabricAsync()
                        updateLoadedState()
                    })
                }
                if (loaderSupports.isQuiltSupports) {
                    add(async {
                        reloadQuiltAsync()
                        updateLoadedState()
                    })
                }
                if (loaderSupports.isCleanroomSupports) {
                    add(async {
                        reloadCleanroomAsync()
                        updateLoadedState()
                    })
                }
            }.awaitAll()
        }
    }

    init {
        reloadAllLoaders()
    }

    override fun onCleared() {
        viewModelScope.cancel()
    }
}

/**
 * 更新模组加载器
 */
@Composable
fun UpdateLoaderScreen(
    mainScreenKey: NavKey?,
    versionsScreenKey: NavKey?,
    version: Version,
    onInstall: (AddonDiffs, GameDownloadInfo) -> Unit
) {
    val versionInfo = version.getVersionInfo() ?: error("Using the \"Loader Update Screen\" is not supported for versions with unspecified version information.")
    val loaderInfo = versionInfo.loaderInfo ?: error("Using the \"Loader Update Screen\" is not supported for unspecified versions of the mod loader.")

    val loaderSupports = rememberLoaderVerSupports(versionInfo.minecraftVersion)

    val viewModel = viewModel(
        key = version.toString() + "_" + "UpdateLoader" + "_" + loaderSupports
    ) {
        AddonsViewModel(
            gameVersion = versionInfo.minecraftVersion,
            loaderInfo = loaderInfo,
            loaderSupports = loaderSupports
        )
    }

    BaseScreen(
        levels1 = listOf(
            Pair(NestedNavKey.VersionSettings::class.java, mainScreenKey)
        ),
        Triple(NormalNavKey.Versions.UpdateLoader, versionsScreenKey, false)
    ) { isVisible ->
        val unLoaded = stringResource(R.string.versions_update_loader_waiting_for_others).takeIf { !viewModel.isLoaded }

        AnimatedLazyColumn(
            modifier = Modifier.fillMaxSize(),
            isVisible = isVisible,
            contentPadding = PaddingValues(all = 12.dp)
        ) { scope ->
            animatedItem(scope) { yOffset ->
                ForgeList(
                    modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                    currentAddon = viewModel.currentAddon,
                    addonList = viewModel.addonList,
                    error = unLoaded,
                    onValueChanged = { viewModel.checkCanUpdate() },
                    onReload = { viewModel.reloadForge() }
                )
            }

            if (loaderSupports.isNeoForgeSupports) {
                animatedItem(scope) { yOffset ->
                    NeoForgeList(
                        modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        currentAddon = viewModel.currentAddon,
                        addonList = viewModel.addonList,
                        error = unLoaded,
                        onValueChanged = { viewModel.checkCanUpdate() },
                        onReload = { viewModel.reloadNeoForge() }
                    )
                }
            }

            if (loaderSupports.isCleanroomSupports) {
                animatedItem(scope) { yOffset ->
                    CleanroomList(
                        modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        currentAddon = viewModel.currentAddon,
                        addonList = viewModel.addonList,
                        error = unLoaded,
                        onValueChanged = { viewModel.checkCanUpdate() },
                        onReload = { viewModel.reloadCleanroom() }
                    )
                }
            }

            if (loaderSupports.isFabricSupports) {
                animatedItem(scope) { yOffset ->
                    FabricList(
                        modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        currentAddon = viewModel.currentAddon,
                        addonList = viewModel.addonList,
                        error = unLoaded,
                        onValueChanged = { viewModel.checkCanUpdate() },
                        onReload = { viewModel.reloadFabric() }
                    )
                }
            }

            if (loaderSupports.isQuiltSupports) {
                animatedItem(scope) { yOffset ->
                    QuiltList(
                        modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        currentAddon = viewModel.currentAddon,
                        addonList = viewModel.addonList,
                        error = unLoaded,
                        onValueChanged = { viewModel.checkCanUpdate() },
                        onReload = { viewModel.reloadQuilt() }
                    )
                }
            }

            animatedItem(scope) { yOffset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        enabled = viewModel.canUpdate,
                        onClick = {
                            onInstall(
                                viewModel.currentAddon.generateDiff(loaderInfo),
                                GameDownloadInfo(
                                    gameVersion = versionInfo.minecraftVersion,
                                    customVersionName = version.getVersionName(),
                                    forge = viewModel.currentAddon.forgeVersion.value,
                                    neoforge = viewModel.currentAddon.neoforgeVersion.value
                                        .takeIf { loaderSupports.isNeoForgeSupports },
                                    fabric = viewModel.currentAddon.fabricVersion.value
                                        .takeIf { loaderSupports.isFabricSupports },
                                    quilt = viewModel.currentAddon.quiltVersion.value
                                        .takeIf { loaderSupports.isQuiltSupports },
                                    cleanroom = viewModel.currentAddon.cleanroomVersion.value
                                        .takeIf { loaderSupports.isCleanroomSupports }
                                )
                            )
                        }
                    ) {
                        Text(text = stringResource(R.string.download_install))
                    }
                }
            }
        }
    }
}