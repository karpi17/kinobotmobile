# Asystent Kinowy — Android App Skeleton

Scaffolding a local-first Android app in **Java** using **Clean Architecture + MVVM**, **Room**, **Apache POI**, **Retrofit2/Gson**, and **Google OAuth 2.0**.

## Proposed Changes

### Project-level Gradle & Manifest

#### [NEW] [build.gradle (project)](file:///j:/kinobot/build.gradle)
Top-level Gradle file with Android Gradle Plugin 8.2.2 and Kotlin plugin classpath.

#### [NEW] [settings.gradle](file:///j:/kinobot/settings.gradle)
Root project name `AsystentKinowy`, include `:app`.

#### [NEW] [gradle.properties](file:///j:/kinobot/gradle.properties)
AndroidX, jetifier, non-transitive R classes.

#### [NEW] [gradlew / gradlew.bat + gradle/wrapper/*](file:///j:/kinobot/gradlew)
Gradle 8.4 wrapper so the project builds out-of-the-box.

---

### App Module

#### [NEW] [app/build.gradle](file:///j:/kinobot/app/build.gradle)
- `minSdk 26`, `targetSdk 34`, `compileSdk 34`
- Dependencies:
  - **Room** (`room-runtime`, `room-compiler`)
  - **Lifecycle** (`lifecycle-viewmodel`, `lifecycle-livedata`)
  - **Retrofit2** (`retrofit`, `converter-gson`)
  - **Gson**
  - **Google Play Services Auth** (`play-services-auth`)
  - **Apache POI** (`poi`, `poi-ooxml`) — with `packaging { exclude … }` for duplicate XML files
  - **AppCompat, Material, ConstraintLayout**

#### [NEW] [app/src/main/AndroidManifest.xml](file:///j:/kinobot/app/src/main/AndroidManifest.xml)
Basic manifest with `INTERNET` permission and a launcher `MainActivity`.

---

### Package Structure (`com.asystent.kinowy`)

```
app/src/main/java/com/asystent/kinowy/
├── models/
│   ├── Shift.java          ← Room Entity
│   └── Loss.java           ← Room Entity
├── db/
│   ├── ShiftDao.java       ← DAO interface
│   ├── LossDao.java        ← DAO interface
│   └── AppDatabase.java    ← RoomDatabase subclass
├── repository/             ← placeholder (empty)
├── network/                ← placeholder (empty)
├── viewmodel/              ← placeholder (empty)
├── ui/
│   └── MainActivity.java   ← minimal launcher activity
```

---

### Room Entities

#### [NEW] [Shift.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/models/Shift.java)

| Column | Type | Notes |
|---|---|---|
| `id` | `int` | `@PrimaryKey(autoGenerate = true)` |
| `date` | `String` | ISO-8601 date (`yyyy-MM-dd`) |
| `startTime` | `String` | `HH:mm` |
| `endTime` | `String` | `HH:mm` |
| `description` | `String` | nullable |
| `confirmed` | `boolean` | default `false` |

#### [NEW] [Loss.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/models/Loss.java)

| Column | Type | Notes |
|---|---|---|
| `id` | `int` | `@PrimaryKey(autoGenerate = true)` |
| `date` | `String` | ISO-8601 date |
| `amount` | `double` | monetary value |
| `description` | `String` | nullable |

---

### DAO Interfaces

#### [NEW] [ShiftDao.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/db/ShiftDao.java)
- `@Insert` — insert single Shift
- `@Update` — update Shift
- `@Delete` — delete Shift
- `@Query("SELECT * FROM shifts ORDER BY date DESC")` — `LiveData<List<Shift>>`
- `@Query("SELECT * FROM shifts WHERE id = :id")` — single Shift by id

#### [NEW] [LossDao.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/db/LossDao.java)
- `@Insert` — insert single Loss
- `@Update` — update Loss
- `@Delete` — delete Loss
- `@Query("SELECT * FROM losses ORDER BY date DESC")` — `LiveData<List<Loss>>`
- `@Query("SELECT * FROM losses WHERE id = :id")` — single Loss by id

#### [NEW] [AppDatabase.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/db/AppDatabase.java)
- `@Database(entities = {Shift.class, Loss.class}, version = 1)`
- Thread-safe singleton via `Room.databaseBuilder()`
- Abstract methods: `shiftDao()`, `lossDao()`

---

## Verification Plan

### Automated Tests
Since this is a skeleton with no runtime logic beyond Room annotations, the primary verification is **compilability**.

- **Gradle sync & build**: `gradlew assembleDebug` (requires Android SDK configured on the machine).

### Manual Verification
1. Open the project in **Android Studio** → confirm Gradle sync completes without errors.
2. Inspect the dependency tree (`./gradlew :app:dependencies`) for correct versions.
3. Verify the package structure matches the plan.

> [!NOTE]
> As requested, I will **stop** after creating the DB layer and present a summary artifact for your approval **before** writing repositories, ViewModels, or UI beyond the basic `MainActivity`.
