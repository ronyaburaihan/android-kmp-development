package com.example.userprofile.settings

import app.cash.turbine.test
import com.example.userprofile.domain.DisplayPreferences
import com.example.userprofile.domain.NameFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Real DataStore over an in-memory Okio file system: the serializer, the storage and the
 * key handling are all exercised, with no disk and no platform, on every target.
 *
 * Each store gets a **globally unique path**. DataStore's "multiple DataStores active for
 * the same file" guard is process-global, keyed by the path string, and released only when
 * the store's scope is cancelled (the default scope never is). It does not know that two
 * `FakeFileSystem`s are different, and a per-instance counter is not enough either: the
 * Kotlin/Native test runner creates a fresh class instance per test method, so a field
 * counter restarts at 0. With a shared path this suite passed on JVM and failed on the iOS
 * simulator with `IllegalStateException: There are multiple DataStores active for the same
 * file`. The production rule is the same: one instance per file, bound as a DI `single`.
 */
class DataStoreSettingsRepositoryTest {

    private fun repository(): DataStoreSettingsRepository {
        val fs = FakeFileSystem()
        val path = "/data/${Random.nextLong()}/${DataStoreSettingsRepository.FILE_NAME}".toPath()
        val store = DataStoreSettingsRepository.create(fs) { path }
        return DataStoreSettingsRepository(store)
    }

    @Test
    fun defaultsWhenNothingStored() = runTest {
        assertEquals(DisplayPreferences(), repository().observeDisplayPreferences().first())
    }

    @Test
    fun persistsAndEmitsWrittenValues() = runTest {
        val repo = repository()
        repo.observeDisplayPreferences().test {
            assertEquals(NameFormat.FULL, awaitItem().nameFormat)

            repo.setNameFormat(NameFormat.INITIALS)
            assertEquals(NameFormat.INITIALS, awaitItem().nameFormat)

            repo.setShowAvatars(false)
            assertEquals(false, awaitItem().showAvatars)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
