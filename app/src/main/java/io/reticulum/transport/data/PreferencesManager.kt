package io.reticulum.transport.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "transport_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val TRANSPORT_ENABLED = booleanPreferencesKey("transport_enabled")
        private val SHARE_INSTANCE = booleanPreferencesKey("share_instance")
        private val INTERFACES_JSON = stringPreferencesKey("interfaces_json")
        private val AUTO_START = booleanPreferencesKey("auto_start")
    }

    val transportEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TRANSPORT_ENABLED] ?: true
    }

    val shareInstance: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHARE_INSTANCE] ?: true
    }

    val interfacesJson: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[INTERFACES_JSON] ?: "[]"
    }

    val autoStart: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_START] ?: false
    }

    suspend fun setTransportEnabled(enabled: Boolean) {
        context.dataStore.edit { it[TRANSPORT_ENABLED] = enabled }
    }

    suspend fun setShareInstance(enabled: Boolean) {
        context.dataStore.edit { it[SHARE_INSTANCE] = enabled }
    }

    suspend fun setInterfacesJson(json: String) {
        context.dataStore.edit { it[INTERFACES_JSON] = json }
    }

    suspend fun setAutoStart(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_START] = enabled }
    }
}
