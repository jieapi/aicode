package com.aicode.feature.credentials.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.gitUserDataStore by preferencesDataStore(name = "git_user_prefs")

/**
 * 持久化 git 提交署名(user.name / user.email)，作为「唯一可信源」。
 *
 * DataStore 存值，落地到容器 `git config --global` 由 UI 在用户保存时显式调用
 * [com.aicode.feature.git.domain.GitRepository.setUserGlobal] 完成——仓储不反向依赖 GitRepository，
 * 职责单一。背景：容器 HOME=/root 全局共享，`--global` 跨工作区生效；但 rootfs 升级会丢
 * `/root/.gitconfig`，故 DataStore 才是真源，进页时按 DataStore 重新同步进 git 全局配置。
 */
@Singleton
class GitUserSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        val USER_NAME_KEY = stringPreferencesKey("git_user_name")
        val USER_EMAIL_KEY = stringPreferencesKey("git_user_email")
    }

    /** 用户昵称流；未设置时视为空串，表示"未配置全局署名，沿用容器既有 .gitconfig"。 */
    val userNameFlow: Flow<String> = context.gitUserDataStore.data.map { it[USER_NAME_KEY] ?: "" }

    val userEmailFlow: Flow<String> = context.gitUserDataStore.data.map { it[USER_EMAIL_KEY] ?: "" }

    suspend fun setUserIdentity(name: String, email: String) {
        context.gitUserDataStore.edit {
            it[USER_NAME_KEY] = name
            it[USER_EMAIL_KEY] = email
        }
    }
}
