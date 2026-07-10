package com.aicodeeditor.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.visionModelDataStore by preferencesDataStore(name = "vision_model_prefs")

/**
 * 持久化「识图专用模型」选择（providerId + model 两字符串）。
 *
 * 识图（viewImage）默认跟随当前聊天模型；当用户在此指定一个支持 Vision 的模型后，
 * 若当前聊天模型不支持图片输入，识图那一轮会临时切换到该专用模型发送，发完恢复聊天模型。
 * DataStore 用法与 [KeepaliveSettingsRepository] / [LogSettingsRepository] 一致。
 *
 * providerId 为空（未配置）即视为「跟随当前聊天模型」。
 */
@Singleton
class VisionModelSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        val PROVIDER_ID_KEY = stringPreferencesKey("vision_provider_id")
        val MODEL_KEY = stringPreferencesKey("vision_model")
    }

    /** 当前持久化的识图专用 providerId 流；未设置时为空字符串（=跟随聊天模型）。 */
    val providerIdFlow: Flow<String> = context.visionModelDataStore.data.map { it[PROVIDER_ID_KEY] ?: "" }

    /** 当前持久化的识图专用 model 流；未设置时为空字符串。 */
    val modelFlow: Flow<String> = context.visionModelDataStore.data.map { it[MODEL_KEY] ?: "" }

    /** 写入识图专用模型（设空字符串即等同 [clear]）。 */
    suspend fun setVisionModel(providerId: String, model: String) {
        context.visionModelDataStore.edit {
            it[PROVIDER_ID_KEY] = providerId
            it[MODEL_KEY] = model
        }
    }

    /** 清空配置——回退到「跟随当前聊天模型」。 */
    suspend fun clear() {
        context.visionModelDataStore.edit {
            it.remove(PROVIDER_ID_KEY)
            it.remove(MODEL_KEY)
        }
    }

    /** 读取一次当前识图专用 providerId（冷读用）。 */
    suspend fun getVisionProviderId(): String = providerIdFlow.first()

    /** 读取一次当前识图专用 model（冷读用）。 */
    suspend fun getVisionModel(): String = modelFlow.first()
}
