# Room and DataStore

**Scope:** Room on KMP, DataStore Preferences on KMP, platform builders, migrations, driver configuration, what belongs in which store.
**Applies to:** the `:data` layer in KMP and Android projects.
**Official sources:**
- <https://developer.android.com/kotlin/multiplatform/room>
- <https://developer.android.com/kotlin/multiplatform/datastore>
- <https://developer.android.com/training/data-storage/room>
- <https://developer.android.com/kotlin/multiplatform>

**Rule levels:** see `../README.md`.

---

## Choosing the store — MUST

| Data | Store |
|---|---|
| Structured, queryable, relational, many rows | **Room** |
| Small key/value preferences (theme, flags, last-synced timestamp) | **DataStore Preferences** |
| Secrets, tokens, credentials | **Neither, unencrypted.** See `../quality/security.md` |
| Large binary blobs, media | the filesystem; store the path |

**MUST NOT** use `SharedPreferences` in new code. **MUST NOT** use `EncryptedSharedPreferences` at all — it is deprecated. See `../deprecations.md`.

**MUST NOT** store a serialized object graph in DataStore Preferences as a workaround for not wanting a database. Preferences has no schema, no migration tooling and no query capability.

---

## Room version line — MUST decide explicitly

Two lines, **both stable**, with different package names:

| Line | Version | Package | What it is |
|---|---|---|---|
| `androidx.room` (Room 2) | **2.8.5** | `androidx.room.*` | The Android line. KMP-capable (Android, iOS, JVM). Supports blocking DAOs, `SupportSQLiteDatabase`, KAPT. |
| `androidx.room3` (Room 3) | **3.0.3** stable (2026-09-09); 3.1.0-alpha01 | `androidx.room3.*` | **KMP-first major version.** `SQLiteDriver` only (no `SupportSQLite`), coroutine-only DAOs (`suspend` or `Flow`), **KSP only**, `withWriteTransaction` / `useReaderConnection`, Flow-based `InvalidationTracker`, targets Android/iOS/JVM/JS/Wasm. |

[OFFICIAL] <https://developer.android.com/jetpack/androidx/releases/room3>

**Decision rule:**

- **New KMP module → Room 3.** It is the line designed for it, and it is stable. Verified compiling and tested on JVM and iOS in `../../examples/user-profile/`.
- **Existing Android app on Room 2 → stay on Room 2** until a migration to Room 3 is approved; it is a new major with breaking API changes (`runInTransaction` → `withWriteTransaction`, `query()`/cursors → `useReaderConnection { usePrepared }`), not a version bump.
- **MUST NOT** mix the two packages in one module — two runtimes, two code generators.

**Repositories:** Room 3's runtime **and its Gradle plugin** are on **Google Maven only**. **MUST** add `google()` to both `pluginManagement.repositories` and `dependencyResolutionManagement.repositories`, even in a module with no Android target. The failure otherwise is `Plugin [id: 'androidx.room3', version: '3.0.3'] was not found`.

**KSP:** KSP 2.x has its own version line (latest 2.3.12), not Kotlin-prefixed. Check its release notes for the supported Kotlin range. KSP **MUST** be registered per target — see below.

The examples below use Room 3; substitute `androidx.room.*` and the `androidx.room` plugin for the Room 2 line.

## Room setup

```toml
# gradle/libs.versions.toml
[versions]
room3  = "3.0.3"
sqlite = "2.7.1"
ksp    = "2.3.12"      # own version line; check KSP release notes for the Kotlin range

[libraries]
androidx-room3-runtime        = { module = "androidx.room3:room3-runtime",        version.ref = "room3" }
androidx-room3-compiler       = { module = "androidx.room3:room3-compiler",       version.ref = "room3" }
androidx-room3-sqlite-wrapper = { module = "androidx.room3:room3-sqlite-wrapper", version.ref = "room3" }
androidx-sqlite-bundled       = { module = "androidx.sqlite:sqlite-bundled",      version.ref = "sqlite" }

[plugins]
ksp            = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidx-room3 = { id = "androidx.room3",          version.ref = "room3" }
```

```kotlin
// shared/build.gradle.kts
plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room3)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room3.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        androidMain.dependencies {
            implementation(libs.androidx.room3.sqlite.wrapper)
        }
    }
}

// MUST register KSP per target — a bare ksp(...) does not cover native
dependencies {
    add("kspAndroid",           libs.androidx.room3.compiler)
    add("kspIosArm64",          libs.androidx.room3.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")   // the Room 3 plugin's extension is `room3`, not `room`
}
```

```kotlin
// WRONG — only the JVM/Android processor runs; iosMain code generation is missing
dependencies {
    ksp(libs.androidx.room3.compiler)
}
```

**MUST** commit the contents of `schemas/`. The exported schema is the input to migration tests; without it a migration cannot be verified.

**MUST** keep the KSP version matched to the Kotlin version. See `../version-matrix.md`.

---

## Room declarations — `commonMain`

```kotlin
// commonMain
@Entity
internal data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val publishedAtEpochMs: Long,
    val isBookmarked: Boolean = false,
)

@Dao
internal interface ArticleDao {
    @Upsert
    suspend fun upsertAll(items: List<ArticleEntity>)

    @Query("SELECT * FROM ArticleEntity ORDER BY publishedAtEpochMs DESC")
    fun observeAll(): Flow<List<ArticleEntity>>

    @Query("SELECT COUNT(*) FROM ArticleEntity")
    suspend fun count(): Int
}

@Database(entities = [ArticleEntity::class], version = 1)
@ConstructedBy(AppDatabaseConstructor::class)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
}

@Suppress("KotlinNoActualForExpect")
internal expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

internal fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
```

**MUST:**

- Annotate the database with `@ConstructedBy` and declare the matching `expect object : RoomDatabaseConstructor<T>`. The `@Suppress("KotlinNoActualForExpect")` is required — the compiler plugin generates the `actual`.
- Call `setDriver(BundledSQLiteDriver())`. [OFFICIAL — the recommended driver for KMP]
- Call `setQueryCoroutineContext(Dispatchers.IO)` so queries are main-safe.
- Declare every DAO function as `suspend`, except Flow-returning observers.
- Keep entities and DAOs `internal`. Only the repository interface is public. See `../architecture/modularization.md`.

### KMP differences from Android-only Room — MUST know [OFFICIAL]

| Feature | KMP | Android-only |
|---|---|---|
| DAO functions | **MUST** be `suspend` | may be blocking |
| Reactive return type | `Flow<List<T>>` | `LiveData<List<T>>` also available |
| Transactions | `useWriterConnection { }` | `withTransaction { }` |
| Migration | `suspend fun migrate(connection: SQLiteConnection)` | `fun migrate(db: SupportSQLiteDatabase)` |
| Driver | `BundledSQLiteDriver` (recommended) | not applicable |

```kotlin
// WRONG in KMP — blocking DAO function; will not compile
@Query("SELECT * FROM ArticleEntity")
fun getAll(): List<ArticleEntity>

// CORRECT
@Query("SELECT * FROM ArticleEntity")
suspend fun getAll(): List<ArticleEntity>
```

---

## Platform database builders

```kotlin
// androidMain
internal fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath("articles.db")
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = dbFile.absolutePath,
    )
}
```

```kotlin
// iosMain
internal fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(name = documentDirectory() + "/articles.db")

private fun documentDirectory(): String {
    val dir = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(dir?.path)
}
```

```kotlin
// jvmMain (desktop)
internal fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "articles.db")
    return Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
}
```

**MUST** supply the builder through the DI platform module, not by calling `getDatabaseBuilder()` from common code — common code cannot see the platform overload. See `koin-di.md`.

**MUST** register the database as a `single`. Two instances over one file produce locking errors and inconsistent caches.

**SHOULD NOT** put a user-data database in the iOS temporary directory or in Android's cache directory — both can be purged by the OS. The `jvmMain` temp-directory example above is from the official docs and is appropriate for a scratch database only.

---

## Migrations — MUST

**MUST** write an explicit migration for every schema version bump.

**MUST NOT** ship `fallbackToDestructiveMigration()` in a release build.

```kotlin
// WRONG — silently deletes all user data on every schema change
Room.databaseBuilder<AppDatabase>(name = path)
    .fallbackToDestructiveMigration(dropAllTables = true)
```

Why the wrong form is a problem: it converts a migration bug into silent data loss, in production, with no error. It is a development convenience only.

**MUST** note the KMP signature difference: a KMP migration receives a `SQLiteConnection`, not a `SupportSQLiteDatabase`. An Android-only migration copied into `commonMain` will not compile.

**Migration testing on KMP — verified.** `androidx.room3:room3-testing` (3.0.3) provides `MigrationTestHelper(schemaDirectoryPath: Path, databasePath: Path, driver: SQLiteDriver, databaseClass: KClass<out RoomDatabase>, databaseFactory: () -> RoomDatabase, autoMigrationSpecs = emptyList())` with `suspend fun createDatabase(version): SQLiteConnection` and `suspend fun runMigrationsAndValidate(version, migrations): SQLiteConnection`. It reads the exported schema JSON from the file system, so it lives in **`jvmTest`** (or `androidDeviceTest`), not `commonTest`. A seeded 1→2 migration test runs in `../../examples/user-profile/src/jvmTest/`.

**MUST** add the R8 keep rule when using Room on Android with minification:

```
-keep class * extends androidx.room3.RoomDatabase { <init>(); }
```

Substitute `androidx.room.RoomDatabase` on the stable line. See `../quality/performance.md`.

---

## DataStore Preferences

**Only DataStore Preferences is supported on KMP** (1.1.0+). **Proto DataStore is not.** [OFFICIAL]

```kotlin
commonMain.dependencies {
    implementation("androidx.datastore:datastore-core:1.2.1")
    implementation("androidx.datastore:datastore-preferences-core:1.2.1")
}
```

```kotlin
// commonMain
internal const val DATA_STORE_FILE_NAME = "settings.preferences_pb"

internal fun createDataStore(storage: Storage<Preferences>): DataStore<Preferences> =
    DataStoreFactory.create(storage = storage)
```

```kotlin
// androidMain
internal fun createDataStore(context: Context): DataStore<Preferences> = createDataStore(
    storage = FileStorage(
        serializer = PreferencesSerializer,
        produceFile = { context.filesDir.resolve(DATA_STORE_FILE_NAME) },
    )
)
```

```kotlin
// iosMain
internal fun createDataStore(): DataStore<Preferences> = createDataStore(
    storage = OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = PreferencesSerializer,
        producePath = {
            val dir: NSURL? = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
            (requireNotNull(dir).path + "/$DATA_STORE_FILE_NAME").toPath()
        },
    )
)
```

Web uses `WebLocalStorage`; JVM desktop uses `FileStorage` with a `File`.

**MUST** keep the filename as a single constant in `commonMain`. A mismatched filename between platforms is a silent data-loss bug — each platform reads an empty store.

**MUST** create the DataStore as a `single`. Multiple instances over one file corrupt state. [UNVERIFIED for the KMP wording; established for Android DataStore — treat as MUST]

### Wrapping DataStore behind a repository — MUST

```kotlin
// CORRECT — :data; the key, the default and the type live in one place
internal class SettingsDataSource(private val dataStore: DataStore<Preferences>) {

    private val darkMode = booleanPreferencesKey("dark_mode")

    fun observeDarkMode(): Flow<Boolean> =
        dataStore.data.map { it[darkMode] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[darkMode] = enabled }
    }
}
```

```kotlin
// WRONG — a ViewModel owning the key and the default
class SettingsViewModel(private val dataStore: DataStore<Preferences>) : ViewModel() {
    private val darkMode = booleanPreferencesKey("dark_mode")
    val uiState = dataStore.data.map { SettingsUiState(it[darkMode] ?: false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())
}
```

Why the wrong form is a problem: it violates the repository rule (`../android/app-architecture.md`), and three screens reading the same preference will each define their own default, so the app disagrees with itself. A typo in the key string in one place silently reads a different preference.

**MUST** handle read failures. `dataStore.data` can emit an error (corrupt file, I/O failure); an unhandled error in a `Flow` collected by the UI crashes the screen.

```kotlin
// CORRECT
fun observeDarkMode(): Flow<Boolean> =
    dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[darkMode] ?: false }
```

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| DAO or DataStore injected into a ViewModel or composable | violates the repository rule; defaults duplicated per screen |
| Blocking DAO function in KMP | does not compile |
| Entity used as the domain model | schema changes propagate to the UI; domain needs Room to compile |
| `fallbackToDestructiveMigration` in release | silent production data loss |
| Not committing exported schemas | migrations cannot be tested |
| Bare `ksp(...)` in a KMP module | native code generation silently missing |
| Multiple `DataStore` or database instances over one file | corruption, locking errors |
| Mismatched DataStore filename across platforms | silent data loss per platform |
| Mixing `androidx.room` and `androidx.room3` packages | two runtimes, two generators; compile errors |
| `google()` missing from `pluginManagement` | the `androidx.room3` plugin cannot be found |
| Secrets or tokens in DataStore Preferences | stored in plaintext — `../quality/security.md` |
| Unhandled `dataStore.data` error | screen crash on a corrupt file |
| Serialized object graph in Preferences | no schema, no migrations, no queries |

---

## Android / iOS differences

| Concern | Android | iOS | Desktop | Web |
|---|---|---|---|---|
| Room support | yes | yes | yes | **no** |
| DataStore support | yes | yes | yes | yes (`WebLocalStorage`) |
| DataStore flavour | Preferences + Proto | **Preferences only** | Preferences only | Preferences only |
| DB path | `context.getDatabasePath(...)` | `NSDocumentDirectory` | chosen path | n/a |
| Backup/sync exposure | Android Auto Backup may include the DB | iCloud may back up `NSDocumentDirectory` | n/a | n/a |
| Extra dependency | `room3-sqlite-wrapper` in `androidMain` | none | none | n/a |
| R8 keep rule | required | not applicable | not applicable | n/a |
| Migration test helper | `MigrationTestHelper` available | **[UNVERIFIED]** | **[UNVERIFIED]** | n/a |

**SHOULD** consider platform backup behaviour when choosing a directory: data in `NSDocumentDirectory` may be backed up to iCloud, and Android Auto Backup may include an app database. For data that **MUST NOT** leave the device, exclude it from backup explicitly on each platform.

---

## Testing recommendations

### Room — in-memory database

```kotlin
// androidDeviceTest or jvmTest, depending on the target
class ArticleDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ArticleDao

    @BeforeTest
    fun setUp() {
        db = inMemoryDatabaseBuilder()        // platform-specific in-memory builder
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        dao = db.articleDao()
    }

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun observeAllOrdersByPublishedDescending() = runTest {
        dao.upsertAll(listOf(older, newer))
        assertEquals(listOf(newer.id, older.id), dao.observeAll().first().map { it.id })
    }
}
```

**MUST** close the database in teardown. A leaked in-memory database holds its connection and the next test sees locking failures.

**MUST** test the SQL, not Room: ordering, filtering, upsert-conflict behaviour, and any `@Query` with a non-trivial `WHERE`. A `@Query` typo is a runtime failure that only a DAO test catches.

### DataStore

```kotlin
@Test
fun defaultsToFalseWhenUnset() = runTest {
    val source = SettingsDataSource(createTestDataStore())
    assertFalse(source.observeDarkMode().first())
}

@Test
fun persistsWrittenValue() = runTest {
    val source = SettingsDataSource(createTestDataStore())
    source.setDarkMode(true)
    assertTrue(source.observeDarkMode().first())
}
```

**MUST** give each test its own store file in a temporary directory, and delete it in teardown. A shared file makes tests order-dependent.

### Repository

**MUST** test the repository with a **fake DAO and fake DataStore**, not a real database — that keeps the test in `commonTest` and runs it on every target.

```kotlin
internal class FakeArticleDao : ArticleDao {
    private val rows = MutableStateFlow<List<ArticleEntity>>(emptyList())
    override suspend fun upsertAll(items: List<ArticleEntity>) {
        rows.update { existing -> (items + existing).distinctBy(ArticleEntity::id) }
    }
    override fun observeAll(): Flow<List<ArticleEntity>> = rows
    override suspend fun count(): Int = rows.value.size
}
```

**SHOULD** test mappers (`toDomain`, `toEntity`) directly in `commonTest`. They are pure functions and they are where field-mismatch bugs live.

Full strategy: `../quality/testing-strategy.md`.

---

## Cross-references

- The repository rule and layer boundaries: `../android/app-architecture.md`
- Entity/domain/DTO separation and mapper placement: `../architecture/clean-architecture.md`
- Builders as platform DI bindings, database as a `single`: `koin-di.md`
- `refresh()` pairing with the network layer: `ktor-networking.md`
- Why not `EncryptedSharedPreferences`, and where secrets go: `../quality/security.md`
- R8 keep rules: `../quality/performance.md`
- Versions and the Room line decision: `../version-matrix.md`
