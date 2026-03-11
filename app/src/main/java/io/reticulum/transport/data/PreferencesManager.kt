package io.reticulum.transport.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "transport_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val TRANSPORT_ENABLED = booleanPreferencesKey("transport_enabled")
        private val SHARE_INSTANCE = booleanPreferencesKey("share_instance")
        private val SHARED_INSTANCE_PORT = intPreferencesKey("shared_instance_port")
        private val INSTANCE_CONTROL_PORT = intPreferencesKey("instance_control_port")
        private val INTERFACES_JSON = stringPreferencesKey("interfaces_json")
        private val AUTO_START = booleanPreferencesKey("auto_start")
        private val PUBLISH_BLACKHOLE = booleanPreferencesKey("publish_blackhole")
        private val BLACKHOLE_SOURCES = stringPreferencesKey("blackhole_sources")
    }

    val transportEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TRANSPORT_ENABLED] ?: true
    }

    val shareInstance: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHARE_INSTANCE] ?: true
    }

    val sharedInstancePort: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SHARED_INSTANCE_PORT] ?: 0
    }

    val instanceControlPort: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[INSTANCE_CONTROL_PORT] ?: 0
    }

    val interfacesJson: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[INTERFACES_JSON] ?: "[]"
    }

    val autoStart: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_START] ?: false
    }

    val publishBlackhole: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PUBLISH_BLACKHOLE] ?: false
    }

    val blackholeSources: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[BLACKHOLE_SOURCES] ?: ""
    }

    suspend fun setTransportEnabled(enabled: Boolean) {
        context.dataStore.edit { it[TRANSPORT_ENABLED] = enabled }
    }

    suspend fun setShareInstance(enabled: Boolean) {
        context.dataStore.edit { it[SHARE_INSTANCE] = enabled }
    }

    suspend fun setSharedInstancePort(port: Int) {
        context.dataStore.edit { it[SHARED_INSTANCE_PORT] = port }
    }

    suspend fun setInstanceControlPort(port: Int) {
        context.dataStore.edit { it[INSTANCE_CONTROL_PORT] = port }
    }

    suspend fun setInterfacesJson(json: String) {
        context.dataStore.edit { it[INTERFACES_JSON] = json }
    }

    suspend fun setAutoStart(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_START] = enabled }
    }

    suspend fun setPublishBlackhole(enabled: Boolean) {
        context.dataStore.edit { it[PUBLISH_BLACKHOLE] = enabled }
    }

    suspend fun setBlackholeSources(sources: String) {
        context.dataStore.edit { it[BLACKHOLE_SOURCES] = sources }
    }
}
