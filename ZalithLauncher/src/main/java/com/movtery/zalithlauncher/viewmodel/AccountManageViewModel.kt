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

package com.movtery.zalithlauncher.viewmodel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.context.copyLocalFile
import com.movtery.zalithlauncher.context.getFileName
import com.movtery.zalithlauncher.coroutine.Task
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.addOtherServer
import com.movtery.zalithlauncher.game.account.auth_server.AuthServerHelper
import com.movtery.zalithlauncher.game.account.auth_server.ResponseException
import com.movtery.zalithlauncher.game.account.auth_server.data.AuthServer
import com.movtery.zalithlauncher.game.account.localLogin
import com.movtery.zalithlauncher.game.account.microsoft.MINECRAFT_SERVICES_URL
import com.movtery.zalithlauncher.game.account.microsoft.MinecraftProfileException
import com.movtery.zalithlauncher.game.account.microsoft.NotPurchasedMinecraftException
import com.movtery.zalithlauncher.game.account.microsoft.XboxLoginException
import com.movtery.zalithlauncher.game.account.microsoft.toLocal
import com.movtery.zalithlauncher.game.account.microsoftLogin
import com.movtery.zalithlauncher.game.account.refreshMicrosoft
import com.movtery.zalithlauncher.game.account.wardrobe.SkinModelType
import com.movtery.zalithlauncher.game.account.wardrobe.getLocalUUIDWithSkinModel
import com.movtery.zalithlauncher.game.account.wardrobe.validateSkinFile
import com.movtery.zalithlauncher.game.account.yggdrasil.cacheAllCapes
import com.movtery.zalithlauncher.game.account.yggdrasil.changeCape
import com.movtery.zalithlauncher.game.account.yggdrasil.executeWithAuthorization
import com.movtery.zalithlauncher.game.account.yggdrasil.getPlayerProfile
import com.movtery.zalithlauncher.game.account.yggdrasil.uploadSkin
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.screens.content.elements.AccountOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.AccountSkinOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.LocalLoginOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.MicrosoftChangeCapeOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.MicrosoftChangeSkinOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.MicrosoftLoginOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.OtherLoginOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.ServerOperation
import com.movtery.zalithlauncher.utils.logging.Logger.lError
import com.movtery.zalithlauncher.utils.network.safeBodyAsJson
import com.movtery.zalithlauncher.utils.string.getMessageOrToString
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.io.FileUtils
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.UUID
import javax.inject.Inject
import io.ktor.client.plugins.ResponseException as KtorResponseException

/**
 * 账号管理界面 UI 状态
 * 
 * @property accounts 已加载的账号列表
 * @property currentAccount 当前选中的账号
 * @property authServers 已添加的验证服务器列表
 * @property microsoftLoginOperation 微软登录操作的中间状态
 * @property microsoftChangeSkinOperation 微软皮肤更换操作的中间状态
 * @property microsoftChangeCapeOperation 微软披风更换操作的中间状态
 * @property localLoginOperation 离线登录操作的中间状态
 * @property otherLoginOperation 第三方验证服务器登录操作的中间状态
 * @property serverOperation 验证服务器管理操作的中间状态
 * @property accountOperation 账号通用管理操作状态
 * @property accountSkinOperationMap 维护每个账号对应的皮肤操作状态（用于 UI 局部更新）
 */
data class AccountManageUiState(
    val accounts: List<Account> = emptyList(),
    val currentAccount: Account? = null,
    val authServers: List<AuthServer> = emptyList(),
    val microsoftLoginOperation: MicrosoftLoginOperation = MicrosoftLoginOperation.None,
    val microsoftChangeSkinOperation: MicrosoftChangeSkinOperation = MicrosoftChangeSkinOperation.None,
    val microsoftChangeCapeOperation: MicrosoftChangeCapeOperation = MicrosoftChangeCapeOperation.None,
    val localLoginOperation: LocalLoginOperation = LocalLoginOperation.None,
    val otherLoginOperation: OtherLoginOperation = OtherLoginOperation.None,
    val serverOperation: ServerOperation = ServerOperation.None,
    val accountOperation: AccountOperation = AccountOperation.None,
    val accountSkinOperationMap: Map<String, AccountSkinOperation> = emptyMap()
)

/**
 * 账号管理界面用户意图 (MVI Intent)
 * 封装了 UI 层发出的所有操作请求
 */
sealed class AccountManageIntent {
    // --- 状态更新意图 (控制 UI 交互流程) ---
    data class UpdateMicrosoftLoginOp(val operation: MicrosoftLoginOperation) :
        AccountManageIntent()

    data class UpdateMicrosoftSkinOp(val operation: MicrosoftChangeSkinOperation) :
        AccountManageIntent()

    data class UpdateMicrosoftCapeOp(val operation: MicrosoftChangeCapeOperation) :
        AccountManageIntent()

    data class UpdateLocalLoginOp(val operation: LocalLoginOperation) : AccountManageIntent()
    data class UpdateOtherLoginOp(val operation: OtherLoginOperation) : AccountManageIntent()
    data class UpdateServerOp(val operation: ServerOperation) : AccountManageIntent()
    data class UpdateAccountOp(val operation: AccountOperation) : AccountManageIntent()
    data class UpdateAccountSkinOp(val accountUuid: String, val operation: AccountSkinOperation) :
        AccountManageIntent()

    // --- 业务执行意图 (触发实际的后台逻辑) ---

    /** 执行微软登录流程 */
    data class PerformMicrosoftLogin(
        val toWeb: (url: String) -> Unit,
        val backToMain: () -> Unit,
        val checkIfInWebScreen: () -> Boolean
    ) : AccountManageIntent()

    /** 导入选中的皮肤文件到缓存目录 */
    data class ImportSkinFile(val account: Account, val uri: Uri) : AccountManageIntent()

    /** 上传皮肤到微软服务器 */
    data class UploadMicrosoftSkin(
        val account: Account,
        val skinFile: File,
        val skinModel: SkinModelType
    ) : AccountManageIntent()

    /** 抓取该账号可用的微软披风列表 */
    data class FetchMicrosoftCapes(val account: Account) : AccountManageIntent()

    /** 应用选中的微软披风 */
    data class ApplyMicrosoftCape(
        val account: Account,
        val capeId: String?,
        val capeName: String,
        val isReset: Boolean
    ) : AccountManageIntent()

    /** 创建新的离线账号 */
    data class CreateLocalAccount(val userName: String, val userUUID: String?) :
        AccountManageIntent()

    /** 使用第三方验证服务器进行登录 */
    data class LoginWithOtherServer(
        val server: AuthServer,
        val email: String,
        val pass: String
    ) : AccountManageIntent()

    /** 添加新的 Yggdrasil 验证服务器 */
    data class AddServer(val url: String) : AccountManageIntent()

    /** 删除指定的验证服务器 */
    data class DeleteServer(val server: AuthServer) : AccountManageIntent()

    /** 删除账号及其相关数据 */
    data class DeleteAccount(val account: Account) : AccountManageIntent()

    /** 刷新账号的登录凭据（Token） */
    data class RefreshAccount(val account: Account) : AccountManageIntent()

    /** 保存离线账号的皮肤文件 */
    data class SaveLocalSkin(val account: Account, val uri: Uri) : AccountManageIntent()

    /** 将账号皮肤重置为默认状态 */
    data class ResetSkin(val account: Account) : AccountManageIntent()
}

/**
 * 账号管理界面单次副作用 (MVI Effect)
 * 用于处理 Toast、错误弹窗或 UI 通知等瞬时事件
 */
sealed class AccountManageEffect {
    /** 在 UI 层显示错误信息对话框 */
    data class ShowError(val title: String, val message: String) : AccountManageEffect()

    /** 在 UI 层显示 Toast 提示 */
    data class ShowToast(
        val messageRes: Int,
        val formatArgs: List<Any> = emptyList(),
        val duration: Int = Toast.LENGTH_SHORT
    ) : AccountManageEffect()

    /** 通知 UI 层对应账号的头像数据已更新，需要重新加载显示 */
    data class RefreshAvatar(val accountUuid: String) : AccountManageEffect()
}

/**
 * 账号管理界面 ViewModel
 * 
 * 核心逻辑处理器，负责将 Intent 转化为状态更新或副作用。
 * 通过 ApplicationContext 避免了 Activity 生命周期导致的内存泄漏。
 * 
 * @property context 全局应用上下文
 */
@HiltViewModel
class AccountManageViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    // 各类细分操作状态流，用于组合成完整的 UI 状态
    private val _microsoftLoginOp =
        MutableStateFlow<MicrosoftLoginOperation>(MicrosoftLoginOperation.None)
    private val _microsoftSkinOp =
        MutableStateFlow<MicrosoftChangeSkinOperation>(MicrosoftChangeSkinOperation.None)
    private val _microsoftCapeOp =
        MutableStateFlow<MicrosoftChangeCapeOperation>(MicrosoftChangeCapeOperation.None)
    private val _localLoginOp = MutableStateFlow<LocalLoginOperation>(LocalLoginOperation.None)
    private val _otherLoginOp = MutableStateFlow<OtherLoginOperation>(OtherLoginOperation.None)
    private val _serverOp = MutableStateFlow<ServerOperation>(ServerOperation.None)
    private val _accountOp = MutableStateFlow<AccountOperation>(AccountOperation.None)
    private val _accountSkinOpMap = MutableStateFlow<Map<String, AccountSkinOperation>>(emptyMap())

    // 用于发送单次副作用的通道
    private val _effect = Channel<AccountManageEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    /**
     * 统一的 UI 状态流
     * 使用 combine 组合了来自持久层 (AccountsManager) 的数据流与 View 层内部的交互状态流
     */
    val uiState: StateFlow<AccountManageUiState> = combine(
        AccountsManager.accountsFlow,
        AccountsManager.currentAccountFlow,
        AccountsManager.authServersFlow,
        _microsoftLoginOp,
        _microsoftSkinOp,
        _microsoftCapeOp,
        _localLoginOp,
        _otherLoginOp,
        _serverOp,
        _accountOp,
        _accountSkinOpMap
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        AccountManageUiState(
            accounts = args[0] as List<Account>,
            currentAccount = args[1] as Account?,
            authServers = args[2] as List<AuthServer>,
            microsoftLoginOperation = args[3] as MicrosoftLoginOperation,
            microsoftChangeSkinOperation = args[4] as MicrosoftChangeSkinOperation,
            microsoftChangeCapeOperation = args[5] as MicrosoftChangeCapeOperation,
            localLoginOperation = args[6] as LocalLoginOperation,
            otherLoginOperation = args[7] as OtherLoginOperation,
            serverOperation = args[8] as ServerOperation,
            accountOperation = args[9] as AccountOperation,
            accountSkinOperationMap = args[10] as Map<String, AccountSkinOperation>
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountManageUiState()
    )

    /**
     * 处理来自 UI 层的所有 Intent
     */
    fun onIntent(intent: AccountManageIntent) {
        when (intent) {
            // 更新 UI 交互状态
            is AccountManageIntent.UpdateMicrosoftLoginOp -> _microsoftLoginOp.value =
                intent.operation

            is AccountManageIntent.UpdateMicrosoftSkinOp -> _microsoftSkinOp.value =
                intent.operation

            is AccountManageIntent.UpdateMicrosoftCapeOp -> _microsoftCapeOp.value =
                intent.operation

            is AccountManageIntent.UpdateLocalLoginOp -> _localLoginOp.value = intent.operation
            is AccountManageIntent.UpdateOtherLoginOp -> _otherLoginOp.value = intent.operation
            is AccountManageIntent.UpdateServerOp -> _serverOp.value = intent.operation
            is AccountManageIntent.UpdateAccountOp -> _accountOp.value = intent.operation
            is AccountManageIntent.UpdateAccountSkinOp -> {
                _accountSkinOpMap.update { it + (intent.accountUuid to intent.operation) }
            }

            // 触发业务逻辑
            is AccountManageIntent.PerformMicrosoftLogin -> performMicrosoftLogin(intent)
            is AccountManageIntent.ImportSkinFile -> importSkinFile(intent)
            is AccountManageIntent.UploadMicrosoftSkin -> uploadMicrosoftSkin(intent)
            is AccountManageIntent.FetchMicrosoftCapes -> fetchMicrosoftCapes(intent)
            is AccountManageIntent.ApplyMicrosoftCape -> applyMicrosoftCape(intent)
            is AccountManageIntent.CreateLocalAccount -> createLocalAccount(
                intent.userName,
                intent.userUUID
            )

            is AccountManageIntent.LoginWithOtherServer -> loginWithOtherServer(intent)
            is AccountManageIntent.AddServer -> addServer(intent.url)
            is AccountManageIntent.DeleteServer -> deleteServer(intent.server)
            is AccountManageIntent.DeleteAccount -> deleteAccount(intent.account)
            is AccountManageIntent.RefreshAccount -> refreshAccount(intent.account)
            is AccountManageIntent.SaveLocalSkin -> saveLocalSkin(intent)
            is AccountManageIntent.ResetSkin -> resetSkin(intent.account)
        }
    }

    /** 内部方法：发送错误通知 */
    private fun emitError(title: String, message: String) {
        viewModelScope.launch {
            _effect.send(AccountManageEffect.ShowError(title, message))
        }
    }

    /** 内部方法：发送 Toast 消息 */
    private fun emitToast(
        messageRes: Int,
        vararg args: Any,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        viewModelScope.launch {
            _effect.send(AccountManageEffect.ShowToast(messageRes, args.toList(), duration))
        }
    }

    /** 内部方法：触发头像重载副作用 */
    private fun emitRefreshAvatar(accountUuid: String) {
        viewModelScope.launch {
            _effect.send(AccountManageEffect.RefreshAvatar(accountUuid))
        }
    }

    // --- 业务逻辑具体实现 ---

    /** 执行微软登录流程 */
    private fun performMicrosoftLogin(intent: AccountManageIntent.PerformMicrosoftLogin) {
        microsoftLogin(
            context,
            intent.toWeb,
            intent.backToMain,
            intent.checkIfInWebScreen,
            { onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(it)) },
            { emitError(it.title, it.message) }
        )
        onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(MicrosoftLoginOperation.None))
    }

    /** 处理皮肤文件导入 */
    private fun importSkinFile(intent: AccountManageIntent.ImportSkinFile) {
        val account = intent.account
        val uri = intent.uri
        val fileName = context.getFileName(uri) ?: UUID.randomUUID().toString().replace("-", "")
        val cacheFile = File(PathManager.DIR_IMAGE_CACHE, fileName)

        TaskSystem.submitTask(
            Task.runTask(
                id = account.uniqueUUID,
                dispatcher = Dispatchers.IO,
                task = {
                    context.copyLocalFile(uri, cacheFile)
                    if (validateSkinFile(cacheFile)) {
                        onIntent(
                            AccountManageIntent.UpdateMicrosoftSkinOp(
                                MicrosoftChangeSkinOperation.SelectSkinModel(account, cacheFile)
                            )
                        )
                    } else {
                        emitError(
                            context.getString(R.string.generic_warning),
                            context.getString(R.string.account_change_skin_invalid)
                        )
                        onIntent(
                            AccountManageIntent.UpdateMicrosoftSkinOp(
                                MicrosoftChangeSkinOperation.None
                            )
                        )
                    }
                },
                onError = { th ->
                    emitError(
                        context.getString(R.string.generic_error),
                        context.getString(R.string.account_change_skin_failed_to_import) + "\r\n" + th.getMessageOrToString()
                    )
                    onIntent(AccountManageIntent.UpdateMicrosoftSkinOp(MicrosoftChangeSkinOperation.None))
                },
                onCancel = {
                    onIntent(AccountManageIntent.UpdateMicrosoftSkinOp(MicrosoftChangeSkinOperation.None))
                })
        )
    }

    /** 上传微软皮肤 */
    private fun uploadMicrosoftSkin(intent: AccountManageIntent.UploadMicrosoftSkin) {
        val account = intent.account
        val skinFile = intent.skinFile
        val skinModel = intent.skinModel

        // 立即重置 UI 状态以关闭对话框
        onIntent(AccountManageIntent.UpdateMicrosoftSkinOp(MicrosoftChangeSkinOperation.None))

        TaskSystem.submitTask(
            Task.runTask(
                dispatcher = Dispatchers.IO,
                task = { task ->
                    executeWithAuthorization(block = {
                        task.updateProgress(-1f, R.string.account_change_skin_uploading)
                        uploadSkin(MINECRAFT_SERVICES_URL, account.accessToken, skinFile, skinModel)
                    }, onRefreshRequest = {
                        account.refreshMicrosoft(task = task, coroutineContext = coroutineContext)
                        AccountsManager.suspendSaveAccount(account)
                    })

                    task.updateMessage(R.string.account_change_skin_update_local)
                    runCatching { account.downloadSkin() }.onFailure { th ->
                        emitError(
                            context.getString(R.string.account_logging_in_failed),
                            formatAccountError(th)
                        )
                    }

                    emitToast(
                        R.string.account_change_skin_update_toast,
                        duration = Toast.LENGTH_LONG
                    )
                },
                onError = { th ->
                    val (title, msg) = if (th is KtorResponseException) {
                        val body = th.response.safeBodyAsJson<JsonObject>()
                        context.getString(
                            R.string.account_change_skin_failed_to_upload,
                            th.response.status.value
                        ) to (body["errorMessage"]?.jsonPrimitive?.contentOrNull
                            ?: th.getMessageOrToString())
                    } else {
                        context.getString(R.string.generic_error) to formatAccountError(th)
                    }
                    emitError(title, msg)
                    onIntent(AccountManageIntent.UpdateMicrosoftSkinOp(MicrosoftChangeSkinOperation.None))
                },
                onCancel = {
                    onIntent(AccountManageIntent.UpdateMicrosoftSkinOp(MicrosoftChangeSkinOperation.None))
                })
        )
    }

    /** 获取微软披风列表 */
    private fun fetchMicrosoftCapes(intent: AccountManageIntent.FetchMicrosoftCapes) {
        val account = intent.account
        TaskSystem.submitTask(
            Task.runTask(
                id = account.uniqueUUID,
                dispatcher = Dispatchers.IO,
                task = { task ->
                    executeWithAuthorization(block = {
                        task.updateProgress(-1f, R.string.account_change_cape_fetch_all)
                        val profile = getPlayerProfile(MINECRAFT_SERVICES_URL, account.accessToken)
                        task.updateProgress(-1f, R.string.account_change_cape_cache_all)
                        cacheAllCapes(profile)
                        onIntent(
                            AccountManageIntent.UpdateMicrosoftCapeOp(
                                MicrosoftChangeCapeOperation.SelectCape(account, profile)
                            )
                        )
                    }, onRefreshRequest = {
                        account.refreshMicrosoft(task = task, coroutineContext = coroutineContext)
                        AccountsManager.suspendSaveAccount(account)
                    })
                },
                onError = { th ->
                    emitError(
                        context.getString(R.string.generic_error),
                        context.getString(R.string.account_change_cape_fetch_all_failed) + "\r\n" + th.getMessageOrToString()
                    )
                    onIntent(AccountManageIntent.UpdateMicrosoftCapeOp(MicrosoftChangeCapeOperation.None))
                },
                onCancel = {
                    onIntent(AccountManageIntent.UpdateMicrosoftCapeOp(MicrosoftChangeCapeOperation.None))
                })
        )
    }

    /** 更改微软账号披风 */
    private fun applyMicrosoftCape(intent: AccountManageIntent.ApplyMicrosoftCape) {
        val account = intent.account
        val capeId = intent.capeId
        val capeName = intent.capeName
        val isReset = intent.isReset

        // 立即重置 UI 状态以关闭对话框
        onIntent(AccountManageIntent.UpdateMicrosoftCapeOp(MicrosoftChangeCapeOperation.None))

        TaskSystem.submitTask(
            Task.runTask(
                id = account.uniqueUUID + "_cape",
                dispatcher = Dispatchers.IO,
                task = { task ->
                    executeWithAuthorization(block = {
                        task.updateMessage(R.string.account_change_cape_apply)
                        changeCape(MINECRAFT_SERVICES_URL, account.accessToken, capeId)
                    }, onRefreshRequest = {
                        account.refreshMicrosoft(task = task, coroutineContext = coroutineContext)
                        AccountsManager.suspendSaveAccount(account)
                    })

                    if (isReset) emitToast(R.string.account_change_cape_apply_reset)
                    else emitToast(R.string.account_change_cape_apply_success, capeName)

                    onIntent(AccountManageIntent.UpdateMicrosoftCapeOp(MicrosoftChangeCapeOperation.None))
                },
                onError = { th ->
                    val (title, msg) = if (th is KtorResponseException) {
                        val body = th.response.safeBodyAsJson<JsonObject>()
                        context.getString(
                            R.string.account_change_cape_apply_failed,
                            th.response.status.value
                        ) to (body["errorMessage"]?.jsonPrimitive?.contentOrNull
                            ?: th.getMessageOrToString())
                    } else {
                        context.getString(R.string.generic_error) to formatAccountError(th)
                    }
                    emitError(title, msg)
                    onIntent(AccountManageIntent.UpdateMicrosoftCapeOp(MicrosoftChangeCapeOperation.None))
                },
                onCancel = {
                    onIntent(AccountManageIntent.UpdateMicrosoftCapeOp(MicrosoftChangeCapeOperation.None))
                })
        )
    }

    /** 创建离线账号 */
    private fun createLocalAccount(userName: String, userUUID: String?) {
        localLogin(userName, userUUID)
        onIntent(AccountManageIntent.UpdateLocalLoginOp(LocalLoginOperation.None))
    }

    /** 第三方 Yggdrasil 服务器登录 */
    private fun loginWithOtherServer(intent: AccountManageIntent.LoginWithOtherServer) {
        AuthServerHelper(intent.server, intent.email, intent.pass, onSuccess = { account, task ->
            task.updateMessage(R.string.account_logging_in_saving)
            account.downloadSkin()
            AccountsManager.suspendSaveAccount(account)
        }, onFailed = {
            onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.OnFailed(it)))
        }).createNewAccount(context) { profiles, select ->
            onIntent(
                AccountManageIntent.UpdateOtherLoginOp(
                    OtherLoginOperation.SelectRole(profiles, select)
                )
            )
        }
    }

    /** 添加自定义验证服务器 */
    private fun addServer(url: String) {
        addOtherServer(url) {
            onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.OnThrowable(it)))
        }
        onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None))
    }

    private fun deleteServer(server: AuthServer) {
        AccountsManager.deleteAuthServer(server)
        onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None))
    }

    private fun deleteAccount(account: Account) {
        AccountsManager.deleteAccount(account)
        onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
    }

    /** 强制刷新账号凭据 */
    private fun refreshAccount(account: Account) {
        AccountsManager.refreshAccount(context, account) {
            onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.OnFailed(it)))
        }
    }

    /** 保存离线账号皮肤到本地存储 */
    private fun saveLocalSkin(intent: AccountManageIntent.SaveLocalSkin) {
        val account = intent.account
        val uri = intent.uri
        val skinFile = account.getSkinFile()
        val cacheFile = File(PathManager.DIR_IMAGE_CACHE, skinFile.name)

        TaskSystem.submitTask(Task.runTask(dispatcher = Dispatchers.IO, task = {
            context.copyLocalFile(uri, cacheFile)
            if (validateSkinFile(cacheFile)) {
                cacheFile.copyTo(skinFile, true)
                FileUtils.deleteQuietly(cacheFile)
                AccountsManager.suspendSaveAccount(account)
                emitRefreshAvatar(account.uniqueUUID)
                onIntent(
                    AccountManageIntent.UpdateAccountSkinOp(
                        account.uniqueUUID,
                        AccountSkinOperation.None
                    )
                )
            } else {
                emitError(
                    context.getString(R.string.generic_warning),
                    context.getString(R.string.account_change_skin_invalid)
                )
                onIntent(
                    AccountManageIntent.UpdateAccountSkinOp(
                        account.uniqueUUID,
                        AccountSkinOperation.None
                    )
                )
            }
        }, onError = { th ->
            FileUtils.deleteQuietly(cacheFile)
            emitError(context.getString(R.string.error_import_image), th.getMessageOrToString())
            emitRefreshAvatar(account.uniqueUUID)
            onIntent(
                AccountManageIntent.UpdateAccountSkinOp(
                    account.uniqueUUID,
                    AccountSkinOperation.None
                )
            )
        }))
    }

    /** 重置皮肤数据 */
    private fun resetSkin(account: Account) {
        TaskSystem.submitTask(Task.runTask(dispatcher = Dispatchers.IO, task = {
            account.apply {
                FileUtils.deleteQuietly(getSkinFile())
                skinModelType = SkinModelType.NONE
                profileId = getLocalUUIDWithSkinModel(username, skinModelType)
                AccountsManager.suspendSaveAccount(this)
                emitRefreshAvatar(account.uniqueUUID)
            }
        }))
        onIntent(
            AccountManageIntent.UpdateAccountSkinOp(
                account.uniqueUUID,
                AccountSkinOperation.None
            )
        )
    }

    /**
     * 将多种异常类型统一转化为用户可读的本地化字符串。
     *
     * @param th 捕获的异常
     * @return 格式化后的错误提示
     */
    fun formatAccountError(th: Throwable): String = when (th) {
        is NotPurchasedMinecraftException -> toLocal(context)
        is MinecraftProfileException -> th.toLocal(context)
        is XboxLoginException -> th.toLocal(context)
        is HttpRequestTimeoutException -> context.getString(R.string.error_timeout)
        is UnknownHostException, is UnresolvedAddressException -> context.getString(R.string.error_network_unreachable)
        is ConnectException -> context.getString(R.string.error_connection_failed)
        is KtorResponseException -> {
            val res = when (th.response.status) {
                HttpStatusCode.Unauthorized -> R.string.error_unauthorized
                HttpStatusCode.NotFound -> R.string.error_notfound
                else -> R.string.error_client_error
            }
            context.getString(res, th.response.status.value)
        }

        is ResponseException -> th.responseMessage
        else -> {
            lError("An unknown exception was caught!", th)
            val errorMessage =
                th.localizedMessage ?: th.message ?: th::class.qualifiedName ?: "Unknown error"
            context.getString(R.string.error_unknown, errorMessage)
        }
    }
}
