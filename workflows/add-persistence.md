# Workflow: Add Persistence

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

**Primitives:** `../process/tdd.md` — DAO against a real in-memory database, migration against the exported schema.

---

## 1. Objective

Persist new data in an existing application: a Room entity/DAO and its migration, or a DataStore preference key, exposed through the data layer.

Scope ends at a repository method returning domain types. **This workflow changes on-device state that already belongs to real users, so migration correctness is the primary risk.**

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| What is being stored, and its shape | yes | **MUST** ask. |
| Lifetime — survives reinstall? uninstall? | yes | **MUST** ask. Determines store and backup handling. |
| Is it sensitive (token, credential, PII)? | yes | **MUST** ask explicitly. Changes the store entirely → D5. |
| Queryable / relational, or single-value? | yes | Determines Room vs DataStore. |
| Expected row count / growth | no | Default: assume growth. Ask if it affects indexing. |
| Default value when unset | yes for DataStore | **MUST** ask. A wrong default is a silent behaviour change. |
| Platforms | no | Default: every platform the module targets. |

---

## 3. Initial project inspection

Run `inspect-project.md`, then:

```bash
# which persistence libraries, and which Room line
grep -rn 'androidx.room3\|androidx.room\|app.cash.sqldelight\|datastore' --include='*.kt' --include='*.toml' . | grep -v build/
# current schema version and existing migrations
grep -rn '@Database' --include='*.kt' . | grep -v build/
ls */schemas/*/ 2>/dev/null; find . -path '*schemas*' -name '*.json' -not -path '*/build/*' | sort | tail -5
grep -rn 'addMigrations\|AutoMigration\|fallbackToDestructiveMigration' --include='*.kt' . | grep -v build/
# existing DataStore usage
grep -rn 'preferencesKey\|booleanPreferencesKey\|stringPreferencesKey\|DataStoreFactory' --include='*.kt' . | grep -v build/
```

**MUST** read one existing entity + DAO + repository chain, and one existing DataStore key, and record:

| Question | Why |
|---|---|
| Room line: `androidx.room` or `androidx.room3`? | Package names differ; **MUST NOT** mix |
| Current `@Database(version = n)` | The new version is `n + 1` |
| Are schemas exported and committed? | Without them, migration cannot be tested |
| Migration style: manual `Migration`, `@AutoMigration`, or none | Match it |
| Is `fallbackToDestructiveMigration` present, and in which build type? | A release-build occurrence is a finding |
| Where is the database builder, and is it a DI `single`? | New DAOs are obtained the same way |
| Driver and query context (`BundledSQLiteDriver`, `setQueryCoroutineContext`) | KMP requirement |
| DataStore: one instance or several? where is the file name constant? | **MUST NOT** add a second instance over the same file |
| Are keys defined in one place or scattered? | Match it |
| Are DAO functions `suspend` / `Flow`-returning? | KMP requires `suspend` |
| Entity ↔ domain mapping location | Mapper goes in the same place |
| R8 keep rules present for Room? | Needed on Android with minification |

**MUST** record the current schema version and the most recent exported schema file. If they disagree, that is a pre-existing defect → report before proceeding.

---

## 4. Step-by-step procedure

### 4.1 Choose the store — MUST justify

| Data | Store |
|---|---|
| Queryable, relational, many rows | Room |
| Small single values — flags, last-sync timestamp, user choice | DataStore Preferences |
| Secrets, tokens, credentials | **Neither unencrypted** → **D5** |
| Large binary / media | filesystem; persist the path only |

**MUST NOT** serialise an object graph into a DataStore preference to avoid a migration. Preferences has no schema, no migration tooling, and no queries.

**MUST NOT** use `SharedPreferences` or `EncryptedSharedPreferences` in new code. See `../references/deprecations.md`.

### 4.2 Confirm the plan — MUST

Present and wait for confirmation:

- store choice and why
- for Room: entity fields and types, indices, the new schema version, and the **exact migration SQL**
- for DataStore: key name, type, and default value
- repository method signatures and domain return types
- whether existing persisted data must be transformed (→ D2)

**MUST** show the migration SQL before writing it. It runs against real user data and cannot be rolled back on-device.

### 4.3 Room: entity and DAO

```kotlin
// CORRECT — internal, transport of persistence only, KMP-compatible DAO
@Entity(
    tableName = "article",
    indices = [Index(value = ["published_at_epoch_ms"])],
)
internal data class ArticleEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "published_at_epoch_ms") val publishedAtEpochMs: Long,
    @ColumnInfo(name = "is_bookmarked", defaultValue = "0") val isBookmarked: Boolean = false,
)

@Dao
internal interface ArticleDao {
    @Upsert
    suspend fun upsertAll(items: List<ArticleEntity>)

    @Query("SELECT * FROM article ORDER BY published_at_epoch_ms DESC")
    fun observeAll(): Flow<List<ArticleEntity>>
}
```

```kotlin
// WRONG in KMP — blocking DAO function; will not compile
@Query("SELECT * FROM article")
fun getAll(): List<ArticleEntity>
```

**MUST:**
- declare the entity and DAO `internal`
- make every DAO function `suspend`, except `Flow`-returning observers (mandatory in KMP, correct everywhere)
- add an explicit `defaultValue` on any new non-null column, so the migration has a value to write
- register the DAO on the existing `@Database` class — **MUST NOT** create a second database
- index columns the new queries filter or sort on

See `../references/libraries/room-datastore.md`.

### 4.4 Room: migration — the critical step

**MUST** bump `@Database(version = n + 1)`.

**MUST** write an explicit migration. **MUST NOT** rely on `fallbackToDestructiveMigration` in any build type that ships.

```kotlin
// CORRECT — additive, non-null column with a default, idempotent-safe ordering
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {          // KMP signature
        connection.execSQL(
            "ALTER TABLE article ADD COLUMN is_bookmarked INTEGER NOT NULL DEFAULT 0"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_article_published_at_epoch_ms " +
                "ON article(published_at_epoch_ms)"
        )
    }
}
```

```kotlin
// WRONG — destroys user data on every schema change, silently, in production
Room.databaseBuilder<AppDatabase>(name = path)
    .fallbackToDestructiveMigration(dropAllTables = true)
```

```kotlin
// WRONG — non-null column with no default; the migration fails on any existing row
connection.execSQL("ALTER TABLE article ADD COLUMN is_bookmarked INTEGER NOT NULL")
```

**MUST:**
- register the migration with `addMigrations(...)` where the database is built
- commit the newly exported schema JSON — it is the input to the migration test
- prefer additive changes. A column rename or type change requires create-copy-drop-rename and **MUST** be shown at 4.2 before being written

**Version-sensitive:** in KMP the migration receives a `SQLiteConnection`, not a `SupportSQLiteDatabase`. An Android-only migration copied into `commonMain` will not compile.

### 4.5 DataStore: key and accessor

```kotlin
// CORRECT — key, default and type in one place, read errors handled
internal class SettingsDataSource(private val dataStore: DataStore<Preferences>) {

    private val darkMode = booleanPreferencesKey("dark_mode")

    fun observeDarkMode(): Flow<Boolean> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { it[darkMode] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[darkMode] = enabled }
    }
}
```

**MUST:**
- define the key once, next to its default
- handle `dataStore.data` errors with `.catch { }` — a corrupt file otherwise crashes the collecting screen
- add the accessor to the **existing** data source for this preference area
- reuse the existing `DataStore` instance

**MUST NOT** create a second `DataStore` over the same file, or duplicate a key string in a second location.

### 4.6 Expose through the repository

**MUST** add the method to the existing repository for this data domain, returning domain types. `suspend` for writes, `Flow` for reads.

**MUST NOT** expose the DAO, the entity, `DataStore<Preferences>`, or a `Preferences.Key` above the data layer. See `../references/android/app-architecture.md`.

### 4.7 Tests

**MUST** add, matching the project's test style:

| Tier | Test | Asserts |
|---|---|---|
| Room DAO | insert/query round trip | fields persist; ordering and filtering correct |
| Room DAO | `Flow` emits on change | observer updates after a write |
| **Room migration** | `n` → `n+1` from the exported schema | schema valid; **pre-existing rows survive with correct values** |
| DataStore | unset key | returns the agreed default |
| DataStore | write then read | value persists |
| DataStore | corrupt/failing read | `.catch` path emits the default, does not throw |
| Repository | read and write | returns domain types |
| Mapper | entity ↔ domain | every field, both directions |

The migration test is the one that matters. **MUST** assert that data written at the old version is still readable and correct at the new version — not merely that the migration runs.

> **[UNVERIFIED]** The KMP equivalent of Android's `MigrationTestHelper` was not established. **MUST** verify what the project's Room line offers. Interim position: test the migration in `androidDeviceTest` where `MigrationTestHelper` is available, and record that other targets are covered only by the shared SQL.

### 4.8 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | **No database exists** and the data warrants Room | (a) add Room (new library, KSP per target, schema directory — structural); (b) DataStore if the data is genuinely small | **MUST** ask. Adding Room to a project without it is a structural change. |
| D2 | Existing persisted data **must be transformed**, not just extended | — | **MUST** stop. Present the exact SQL and a rollback story. **MUST NOT** write a transforming migration without approval. |
| D3 | Change requires a **destructive migration** (rename, type change, table drop) | (a) create-copy-drop-rename; (b) additive-only redesign; (c) accept data loss for this table | **MUST** ask, with the user-visible consequence of (c) stated plainly. |
| D4 | `fallbackToDestructiveMigration` found **in a shipping build type** | (a) remove it as a prerequisite (separate change); (b) proceed and report | **MUST** report. Recommend (a) as its own task — it is a live data-loss risk independent of this one. |
| D5 | Data is **sensitive** | (a) platform secure storage behind a domain interface (Keystore / Keychain); (b) encrypt the payload with a Keystore-held key | **MUST** stop. **MUST NOT** store secrets in Room or DataStore unencrypted. **MUST NOT** use `EncryptedSharedPreferences`. See `../references/quality/security.md`. |
| D6 | Project mixes `androidx.room` and `androidx.room3` | — | **MUST** stop and report. Mixed packages mean two runtimes. |
| D7 | Schemas are **not exported** or not committed | (a) enable `schemaDirectory` and commit the current schema first; (b) proceed without a migration test | **MUST** ask. Strongly recommend (a) — without it the migration is untestable. |
| D8 | Data **must not** be included in platform backup (iCloud / Auto Backup) | — | **MUST** ask and implement the exclusion per platform. Default behaviour includes it. |
| D9 | KMP project, persistence needed only on Android | (a) place in the Android module; (b) `commonMain` with platform builders | **MUST** ask. Recommend (a) if iOS has no use for it. |

---

## 6. Implementation rules

**MUST:**

1. Extend the existing database, DataStore instance, and data source. Never add a parallel one.
2. Keep entities, DAOs, keys, and mappers `internal`.
3. Make every new non-null column carry an explicit `defaultValue`.
4. Bump the schema version by exactly one, and commit the exported schema.
5. Register migrations where the database is built.
6. Index columns that new queries filter or sort on.
7. Use the project's Room line consistently.
8. Keep `BundledSQLiteDriver` and `setQueryCoroutineContext` as the project already configures them.
9. Add the Room R8 keep rule if minification is on and it is absent.

**MUST NOT:**

10. Use `fallbackToDestructiveMigration` in any shipping build type.
11. Change an existing entity's column name or type without D3.
12. Create a second `DataStore` or `RoomDatabase` instance over the same file.
13. Duplicate a preference key string in more than one place.
14. Expose a DAO, entity, `DataStore`, or `Preferences.Key` above the data layer.
15. Store a token, credential, or PII unencrypted.
16. Change the DataStore file name — it orphans every existing user's data.
17. Add `SharedPreferences` or `EncryptedSharedPreferences`.
18. Leave `dataStore.data` without a `.catch`.
19. Write a blocking DAO function in a KMP module.

---

## 7. Validation requirements

| # | Check | Command | Required |
|---|---|---|---|
| V1 | Compiles — all targets | `./gradlew :<module>:compileDebugKotlin` (+ KMP native) | MUST |
| V2 | KSP generated code for **every** target | confirm generated sources exist per target; `add("kspIosArm64", ...)` present | MUST if KMP |
| V3 | Exported schema regenerated and committed | `git status` shows the new `schemas/*/<n+1>.json` | MUST if Room |
| V4 | **Migration test passes with pre-existing data** | `./gradlew :<module>:connectedAndroidTest --tests '*Migration*'` | MUST if Room schema changed |
| V5 | DAO tests pass | module test task | MUST if Room |
| V6 | DataStore default / persist / corrupt-read tests pass | module test task | MUST if DataStore |
| V7 | Mapper tests pass | module test task | MUST |
| V8 | Existing persistence tests still pass | whole-module test task | MUST |
| V9 | No `fallbackToDestructiveMigration` in a shipping build type | `grep` | MUST |
| V10 | **Upgrade-in-place smoke test** — install the previous release, then the new build, confirm data survives | manual on a device | MUST if Room schema changed |
| V11 | Room R8 keep rule present | `grep` the proguard/keep files | MUST if minification on |
| V12 | Lint / format clean | project task | MUST if configured |

V10 is the check that catches what unit tests miss. **MUST NOT** report a schema change as done without it, or without recording it as `NOT RUN`.

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Room reports a schema mismatch at runtime | The migration does not produce the schema Room expects. **MUST** diff the exported schema JSONs and fix the SQL. **MUST NOT** silence it with `fallbackToDestructiveMigration`. |
| Migration fails on existing rows | Almost always a non-null column without a default. **MUST** add `DEFAULT`; **MUST NOT** make the column nullable just to pass. |
| Migration test passes, upgrade-in-place fails | The test did not seed representative data. **MUST** seed rows at the old version first, then migrate, then assert values. |
| KSP generates nothing for a native target | The processor is not registered for that target. **MUST** add `add("ksp<Target>", ...)`. A bare `ksp(...)` covers only JVM/Android. |
| Native compile fails on the DAO | A blocking DAO function, or an Android-only type in `commonMain`. **MUST** fix the signature or relocate the code. |
| DataStore returns empty on one platform only | Usually a divergent file name or path. **MUST** check the shared constant is actually shared. |
| `dataStore.data` throws in production | Missing `.catch`. **MUST** add it and emit the default. |
| Existing persistence test breaks | **MUST** stop. A schema or key change has altered behaviour other code depends on. **MUST NOT** modify or skip the test. |
| Schemas were never exported | → D7. **MUST NOT** fabricate a prior schema file. |
| Data loss observed during testing | **MUST** stop immediately and report. Do not continue iterating on a migration that has destroyed data in a test — restate the plan at 4.2 first. |

---

## 9. Completion criteria

**MUST** all hold:

1. Store choice justified and recorded.
2. Room: entity + DAO added to the existing database; version bumped by one; explicit migration written and registered; new schema JSON committed.
3. DataStore: key defined once with its default; `.catch` present; existing instance and file name reused.
4. Repository exposes domain types only; no persistence type leaks upward.
5. Tests from 4.7 added and passing, including a migration test that asserts **pre-existing data survives**.
6. V1–V9, V11, V12 `PASS` or explicitly `NOT RUN` with a reason.
7. V10 upgrade-in-place performed, or explicitly `NOT RUN` and the Outcome downgraded to `DONE WITH CAVEATS`.
8. No `fallbackToDestructiveMigration` in a shipping build type as a result of this change.
9. No existing column renamed or retyped without approval.
10. Sensitive data not placed in an unencrypted store.
11. No existing test modified or skipped.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Add Persistence — <what is stored>

### Outcome
<DONE | DONE WITH CAVEATS | BLOCKED | NEEDS DECISION> — one sentence.

### Inspection findings
- Persistence: <Room line + version | DataStore | both>
- Schema version: <n> → <n+1>
- Schemas exported/committed: <yes | no (D7)>
- Migration style: <manual Migration | AutoMigration | none>
- `fallbackToDestructiveMigration`: <absent | present in <build type> (D4)>
- Database/DataStore DI: `path:line`, scope=<single?>
- Pattern source: `path`

### Storage decision
| | |
|---|---|
| Data | |
| Store chosen | |
| Why | |
| Sensitive | <no | yes — handled per D5> |
| Lifetime | |
| Default when unset | |
| Backup exposure | <included | excluded per D8> |

### Schema change (Room)
**Version:** <n> → <n+1>

| Column | Type | Null | Default | Indexed |
|---|---|---|---|---|

**Migration SQL — exactly as shipped:**
```sql
ALTER TABLE ...
```

**Data impact:** <additive, no existing rows altered | transforms existing rows — approved at D2>

### Changes made
| File | Change |
|---|---|

### Conventions followed
<Matched patterns with source files. Deliberate deviations from `../references/`
with reasons.>

### Validation performed
| # | Check | Command | Result |
|---|---|---|---|
| V1 | Compile | | |
| V2 | KSP per target | | |
| V3 | Schema exported + committed | | |
| V4 | Migration test with pre-existing data | | |
| V5 | DAO tests | | |
| V6 | DataStore tests | | |
| V7 | Mapper tests | | |
| V8 | Existing persistence tests | | |
| V9 | No destructive fallback | | |
| V10 | **Upgrade-in-place on device** | | |
| V11 | Room R8 keep rule | | |
| V12 | Lint / format | | |

### Tests added
| Test | Covers |
|---|---|

### Migration risk assessment
- Reversible on-device: **no** — a downgrade is not supported
- Rows affected: <estimate or "all rows in `table`">
- Failure mode if the migration throws: <app crashes on open — state it plainly>
- Recommended rollout: <staged, with halt criteria — see `prepare-release.md`>

### Not done
### Observations
### Decisions needed
```
