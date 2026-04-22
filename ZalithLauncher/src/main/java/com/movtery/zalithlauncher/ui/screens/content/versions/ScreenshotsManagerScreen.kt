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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionFolders
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.CardTitleLayout
import com.movtery.zalithlauncher.ui.components.ScalingLabel
import com.movtery.zalithlauncher.ui.components.SimpleTextInputField
import com.movtery.zalithlauncher.ui.components.itemLayoutColor
import com.movtery.zalithlauncher.ui.components.itemLayoutShadowElevation
import com.movtery.zalithlauncher.ui.screens.NestedNavKey
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.TitledNavKey
import com.movtery.zalithlauncher.ui.screens.content.elements.SortByDropdownMenu
import com.movtery.zalithlauncher.ui.screens.content.elements.SortByEnum
import com.movtery.zalithlauncher.ui.screens.content.versions.elements.DeleteAllOperation
import com.movtery.zalithlauncher.ui.screens.content.versions.elements.LoadingState
import com.movtery.zalithlauncher.ui.screens.content.versions.elements.MinecraftColorTextNormal
import com.movtery.zalithlauncher.ui.screens.content.versions.layouts.VersionChunkBackground
import com.movtery.zalithlauncher.utils.animation.getAnimateTween
import com.movtery.zalithlauncher.utils.animation.swapAnimateDpAsState
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 数据模型
data class ScreenshotInfo(
    val file: File,
    val name: String = file.nameWithoutExtension,
    val lastModified: Long = file.lastModified(),
    val size: Long = file.length()
)

data class ScreenshotFilter(val filterName: String)

private class ScreenshotsManageViewModel(
    val screenshotDir: File
) : ViewModel() {
    var filter by mutableStateOf(ScreenshotFilter(""))
        private set

    var allScreenshots by mutableStateOf<List<ScreenshotInfo>>(emptyList())
        private set
    var filteredScreenshots by mutableStateOf<List<ScreenshotInfo>?>(null)
        private set
    // 默认按文件名排序
    var sortByEnum by mutableStateOf(SortByEnum.Name)
        private set
    // 默认倒序排列
    var isAscending by mutableStateOf(false)
        private set

    var listState by mutableStateOf<LoadingState>(LoadingState.None)
        private set

    val selectedFiles = mutableStateListOf<File>()
    var deleteAllOperation by mutableStateOf<DeleteAllOperation>(DeleteAllOperation.None)

    fun selectAllFiles() {
        allScreenshots.forEach { info ->
            if (!selectedFiles.contains(info.file)) selectedFiles.add(info.file)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            listState = LoadingState.Loading
            selectedFiles.clear()

            withContext(Dispatchers.IO) {
                val tempList = mutableListOf<ScreenshotInfo>()
                try {
                    if (screenshotDir.exists() && screenshotDir.isDirectory) {
                        screenshotDir.listFiles { file -> 
                            file.isFile && file.extension.lowercase() == "png" 
                        }?.forEach { file ->
                            ensureActive()
                            tempList.add(ScreenshotInfo(file))
                        }
                    }
                } catch (_: CancellationException) {
                    return@withContext
                }
                allScreenshots = tempList
                applyFilterAndSort()
            }

            listState = LoadingState.None
        }
    }

    init {
        refresh()
    }

    fun updateFilter(newFilter: ScreenshotFilter) {
        this.filter = newFilter
        applyFilterAndSort()
    }

    fun updateSortBy(sortByEnum: SortByEnum) {
        this.sortByEnum = sortByEnum
        applyFilterAndSort()
    }

    fun updateSortOrder() {
        this.isAscending = !this.isAscending
        applyFilterAndSort()
    }

    val supportedSortByEnums = listOf(
        SortByEnum.Name, SortByEnum.FileModifiedTime
    )

    private fun applyFilterAndSort() {
        filteredScreenshots = allScreenshots
            .takeIf { it.isNotEmpty() }
            ?.filter { info ->
                if (filter.filterName.isBlank()) true
                else info.name.contains(filter.filterName, ignoreCase = true)
            }
            ?.sortedWith { o1, o2 ->
                val value = when (sortByEnum) {
                    SortByEnum.Name -> o1.name.compareTo(o2.name)
                    SortByEnum.FileModifiedTime -> o1.lastModified.compareTo(o2.lastModified)
                    else -> error("This sorting method is not supported: $sortByEnum")
                }
                if (isAscending) value else -value
            }
    }
}

@Composable
private fun rememberScreenshotsManageViewModel(
    screenshotDir: File,
    version: Version
) = viewModel(
    key = version.toString() + "_" + VersionFolders.SCREENSHOTS.folderName
) {
    ScreenshotsManageViewModel(screenshotDir)
}

// 屏幕主入口
@Composable
fun ScreenshotsManagerScreen(
    mainScreenKey: TitledNavKey?,
    versionsScreenKey: TitledNavKey?,
    version: Version,
    backToMainScreen: () -> Unit,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    if (!version.isValid()) {
        backToMainScreen()
        return
    }

    val screenshotDir = remember(version) {
        VersionFolders.SCREENSHOTS.getDir(version.getGameDir())
    }

    BaseScreen(
        levels1 = listOf(
            Pair(NestedNavKey.VersionSettings::class.java, mainScreenKey)
        ),
        Triple(NormalNavKey.Versions.ScreenshotsManager, versionsScreenKey, false) 
    ) { isVisible ->
        val viewModel = rememberScreenshotsManageViewModel(screenshotDir, version)

        DeleteAllOperation(
            operation = viewModel.deleteAllOperation,
            changeOperation = { viewModel.deleteAllOperation = it },
            submitError = submitError,
            onRefresh = { viewModel.refresh() }
        )

        val yOffset by swapAnimateDpAsState(
            targetValue = (-40).dp,
            swapIn = isVisible
        )

        VersionChunkBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(all = 12.dp)
                .offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
            paddingValues = PaddingValues()
        ) {
            when (viewModel.listState) {
                is LoadingState.None -> {
                    Column {
                        ScreenshotHeader(
                            modifier = Modifier.fillMaxWidth(),
                            filter = viewModel.filter,
                            changeFilter = { viewModel.updateFilter(it) },
                            supportedSortByEnums = viewModel.supportedSortByEnums,
                            sortByEnum = viewModel.sortByEnum,
                            onSortByChanged = { viewModel.updateSortBy(it) },
                            isAscending = viewModel.isAscending,
                            onToggleSortOrder = { viewModel.updateSortOrder() },
                            onDeleteAll = {
                                if (viewModel.deleteAllOperation == DeleteAllOperation.None && viewModel.selectedFiles.isNotEmpty()) {
                                    viewModel.deleteAllOperation = DeleteAllOperation.Warning(viewModel.selectedFiles)
                                }
                            },
                            isFilesSelected = viewModel.selectedFiles.isNotEmpty(),
                            onSelectAll = { viewModel.selectAllFiles() },
                            onClearFilesSelected = { viewModel.selectedFiles.clear() },
                            onRefresh = { viewModel.refresh() }
                        )

                        ScreenshotGrid(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            list = viewModel.filteredScreenshots,
                            selectedFiles = viewModel.selectedFiles,
                            removeFromSelected = { viewModel.selectedFiles.remove(it) },
                            addToSelected = { viewModel.selectedFiles.add(it) }
                        )
                    }
                }
                is LoadingState.Loading -> {
                    Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

// 顶部控制栏
@Composable
private fun ScreenshotHeader(
    modifier: Modifier = Modifier,
    filter: ScreenshotFilter,
    changeFilter: (ScreenshotFilter) -> Unit,
    supportedSortByEnums: List<SortByEnum>,
    sortByEnum: SortByEnum,
    onSortByChanged: (SortByEnum) -> Unit,
    isAscending: Boolean,
    onToggleSortOrder: () -> Unit,
    onDeleteAll: () -> Unit,
    isFilesSelected: Boolean,
    onSelectAll: () -> Unit,
    onClearFilesSelected: () -> Unit,
    onRefresh: () -> Unit,
    inputFieldColor: Color = itemLayoutColor(),
    inputFieldContentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    CardTitleLayout(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    var expanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.Sort,
                            contentDescription = stringResource(R.string.sort_by)
                        )
                    }
                    SortByDropdownMenu(
                        expanded = expanded,
                        onClose = { expanded = false },
                        enums = supportedSortByEnums,
                        currentEnum = sortByEnum,
                        onEnumChanged = onSortByChanged,
                        isAscending = isAscending,
                        onToggleSortOrder = onToggleSortOrder
                    )
                }

                SimpleTextInputField(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    value = filter.filterName,
                    onValueChange = { changeFilter(ScreenshotFilter(it)) },
                    hint = {
                        Text(
                            text = stringResource(R.string.generic_search),
                            style = TextStyle(color = LocalContentColor.current).copy(fontSize = 12.sp)
                        )
                    },
                    color = inputFieldColor,
                    contentColor = inputFieldContentColor,
                    singleLine = true
                )

                AnimatedVisibility(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    visible = isFilesSelected
                ) {
                    Row {
                        IconButton(onClick = onDeleteAll) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                        }
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Default.SelectAll, contentDescription = null)
                        }
                        IconButton(onClick = { if (isFilesSelected) onClearFilesSelected() }) {
                            Icon(Icons.Default.Deselect, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        VerticalDivider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }

                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.generic_refresh)
                    )
                }
            }
        }
    }
}

// 网格列表视图
@Composable
private fun ScreenshotGrid(
    modifier: Modifier = Modifier,
    list: List<ScreenshotInfo>?,
    selectedFiles: List<File>,
    removeFromSelected: (File) -> Unit,
    addToSelected: (File) -> Unit
) {
    list?.let { items ->
        if (items.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = modifier,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { info ->
                    ScreenshotItemLayout(
                        info = info,
                        selected = selectedFiles.contains(info.file),
                        onClick = {
                            if (selectedFiles.contains(info.file)) {
                                removeFromSelected(info.file)
                            } else {
                                addToSelected(info.file)
                            }
                        }
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                ScalingLabel(
                    modifier = Modifier.align(Alignment.Center),
                    text = stringResource(R.string.generic_no_matching_items)
                )
            }
        }
    } ?: run {
        Box(modifier = Modifier.fillMaxSize()) {
            ScalingLabel(
                modifier = Modifier.align(Alignment.Center),
                text = "暂无截屏"
            )
        }
    }
}

// 单个网格卡片
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenshotItemLayout(
    modifier: Modifier = Modifier,
    info: ScreenshotInfo,
    selected: Boolean,
    onClick: () -> Unit = {},
    itemColor: Color = itemLayoutColor(),
    itemContentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = MaterialTheme.shapes.large,
    shadowElevation: Dp = itemLayoutShadowElevation()
) {
    val borderWidth by animateDpAsState(if (selected) 2.dp else (-1).dp)
    val scale = remember { Animatable(initialValue = 0.95f) }
    
    LaunchedEffect(Unit) {
        scale.animateTo(targetValue = 1f, animationSpec = getAnimateTween())
    }

    Surface(
        modifier = modifier
            .graphicsLayer(scaleY = scale.value, scaleX = scale.value)
            .border(width = borderWidth, color = borderColor, shape = shape),
        onClick = onClick,
        shape = shape,
        color = itemColor,
        contentColor = itemContentColor,
        shadowElevation = shadowElevation
    ) {
        Box(modifier = Modifier.aspectRatio(16f / 9f)) {
            // 使用 Coil 加载图片缩略图
            AsyncImage(
                model = info.file,
                contentDescription = info.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 底部仅保留信息条（无菜单按钮）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                MinecraftColorTextNormal(
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    inputText = info.name,
                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                    maxLines = 1
                )
            }
        }
    }
}