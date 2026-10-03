package com.example.userprofile.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.userprofile.domain.DisplayPreferences
import com.example.userprofile.domain.NameFormat
import com.example.userprofile.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.FileSystem
import okio.Path

/**
 * DataStore Preferences as the implementation of the domain's [SettingsRepository].
 *
 * Only Preferences DataStore is supported on KMP; Proto DataStore is not. Storage is
 * `OkioStorage` from `datastore-core-okio`, which works on every target — the platform
 * supplies only a [Path] (`context.filesDir` on Android, `NSDocumentDirectory` on iOS).
 * See references/libraries/room-datastore.md § DataStore Preferences.
 *
 * Rules this class demonstrates:
 *  - Keys, defaults and the file name live in **one** place. A key string duplicated in a
 *    ViewModel is a silent "reads a different preference" bug.
 *  - `dataStore.data` can throw (corrupt file, I/O). `.catch { emit(emptyPreferences()) }`
 *    degrades to defaults instead of crashing the collecting screen.
 *  - The domain never sees `Preferences` or a `Preferences.Key`; it sees [DisplayPreferences].
 */
public class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val nameFormat = stringPreferencesKey("name_format")
        val showAvatars = booleanPreferencesKey("show_avatars")
    }

    override fun observeDisplayPreferences(): Flow<DisplayPreferences> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                DisplayPreferences(
                    nameFormat = prefs[Keys.nameFormat]?.let(::parseNameFormat) ?: NameFormat.FULL,
                    showAvatars = prefs[Keys.showAvatars] ?: true,
                )
            }

    public suspend fun setNameFormat(format: NameFormat) {
        dataStore.edit { it[Keys.nameFormat] = format.name }
    }

    public suspend fun setShowAvatars(show: Boolean) {
        dataStore.edit { it[Keys.showAvatars] = show }
    }

    // A stored value from an older build may not parse; default rather than crash.
    private fun parseNameFormat(raw: String): NameFormat? =
        NameFormat.entries.firstOrNull { it.name == raw }

    public companion object {
        /** One constant, shared by every platform. A divergent name = empty store per platform. */
        public const val FILE_NAME: String = "settings.preferences_pb"

        /**
         * Platform-agnostic factory. The caller MUST keep exactly one instance per file —
         * two `DataStore`s over one path corrupt it. Bind as a DI `single`.
         */
        public fun create(fileSystem: FileSystem, producePath: () -> Path): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                storage = OkioStorage(
                    fileSystem = fileSystem,
                    serializer = androidx.datastore.preferences.core.PreferencesSerializer,
                    producePath = producePath,
                ),
            )
    }
}
