package com.example.userprofile.database

import androidx.room3.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A real Room database, in memory, on every target — JVM and iOS simulator both run this.
 * It tests the SQL, which is the only thing a DAO test is for: a `@Query` typo is a runtime
 * failure nothing else catches.
 *
 * MUST close in teardown: a leaked in-memory database holds its connection and the next
 * test sees locking failures.
 */
class UserRowDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UserRowDao

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().configure(Dispatchers.Default)
        dao = db.userRowDao()
    }

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun observeEmitsNullForUnknownId() = runTest {
        assertNull(dao.observe("missing").first())
    }

    @Test
    fun upsertInsertsThenUpdatesInPlace() = runTest {
        dao.upsert(UserRow("u1", "Ada", "ada@example.com", null))
        dao.upsert(UserRow("u1", "Ada Lovelace", "ada@example.com", "https://x/a.png"))

        assertEquals(1, dao.count())
        val row = dao.observe("u1").first()
        assertEquals("Ada Lovelace", row?.displayName)
        assertEquals("https://x/a.png", row?.avatarUrl)
    }
}
