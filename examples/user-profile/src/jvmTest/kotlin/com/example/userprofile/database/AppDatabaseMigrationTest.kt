package com.example.userprofile.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The migration test that matters: data written at version 1 is still readable and correct
 * at version 2. A test that only runs the migration proves the SQL parses; this one seeds a
 * row first. See workflows/add-persistence.md § 4.7.
 *
 * jvmTest, not commonTest: `MigrationTestHelper` reads the exported schema JSON from the
 * file system (`schemas/`), which is why the schema directory MUST be committed.
 */
class AppDatabaseMigrationTest {

    private val schemaDir = File(System.getProperty("user.dir"), "schemas").toPath()   // the helper appends the database class name

    private fun helper(dbFile: File) = MigrationTestHelper(
        schemaDirectoryPath = schemaDir,
        databasePath = dbFile.toPath(),
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
        databaseFactory = { AppDatabaseConstructor.initialize() },
    )

    @Test
    fun migrate1To2PreservesExistingRowsAndAppliesDefault() = runTest {
        val dbFile = Files.createTempFile("migration-", ".db").toFile().also { it.delete() }
        val helper = helper(dbFile)

        // Seed at version 1 — the schema the helper reads from schemas/.../1.json.
        helper.createDatabase(1).use { connection ->
            connection.execSQL(
                "INSERT INTO user (id, displayName, email, avatarUrl) VALUES ('u1', 'Ada Lovelace', 'ada@example.com', NULL)"
            )
        }

        // Migrate and validate against schemas/.../2.json.
        helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).use { connection ->
            connection.prepare("SELECT displayName, is_bookmarked FROM user WHERE id = 'u1'").use { stmt ->
                assertEquals(true, stmt.step(), "seeded row must survive the migration")
                assertEquals("Ada Lovelace", stmt.getText(0))
                assertEquals(0L, stmt.getLong(1), "new NOT NULL column takes its default")
            }
        }
        dbFile.delete()
    }
}
