# KinoBot 🎬🍿

> **Cześć! / Hi! 👋** — *Scroll down for the English version.*

![Platform](https://img.shields.io/badge/platform-Android-green?logo=android)
![Language](https://img.shields.io/badge/language-Java-orange?logo=java)
![Min SDK](https://img.shields.io/badge/minSdk-26-blue)
![License](https://img.shields.io/badge/license-MIT-lightgrey)
![Status](https://img.shields.io/badge/status-Active%20Development-brightgreen)

---

## 🇵🇱 O projekcie

Nazywam się Kacper i zbudowałem KinoBota.

Grafiki w kinie to chaos. Zmiany wpadają mailem w Excelu, często na ostatnią chwilę, a praca nierzadko kończy się w środku nocy (tzw. „zamki"). Zamiast ręcznie przepisywać to do kalendarza i liczyć godziny w arkuszu, napisałem aplikację, która robi to za mnie — i działa od miesięcy jako moje codzienne narzędzie pracy.

To **nie jest** projekt z tutoriala. Rozwiązuje realne, operacyjne problemy, z którymi zderzam się każdego tygodnia.

### 🔥 Główne funkcje

*   **Automatyzacja Gmail & Excel** — apka łączy się z Gmailem (OAuth 2.0), nasłuchuje na maile z grafikiem, pobiera załączniki `.xlsx` i parsuje je w tle (Apache POI). Magia dzieje się sama.
*   **Architektura SSoT (Single Source of Truth)** — inteligentny algorytm łączenia danych. Kiedy biorę za kogoś zastępstwo lub ręcznie zmieniam godziny, aplikacja oznacza te rekordy jako "chronione". Kolejny automatyczny import z Excela ich nie nadpisuje.
*   **Pełnoekranowy budzik, testowany na urządzeniu** — system alarmów napisany od zera, przetestowany na Samsung S24 FE (Android 16 / One UI 8.5):
    *   Full-Screen Intent wybudza telefon z ekranem zablokowanym.
    *   Obsługuje zmiany nocne przechodzące przez północ.
    *   Drzemka (snooze) z dokładnym timerem (exact alarm, +10 min).
    *   Przeżywa restart telefonu (`BOOT_COMPLETED`).
    *   Automatycznie anuluje i przebudowuje alarm po edycji godziny zmiany.
*   **Z kim dzisiaj pracuję?** — algorytm przecinania przedziałów czasowych. Aplikacja analizuje cały grafik kina i pokazuje mi listę osób na tej samej zmianie.
*   **Kalkulator finansów** — miesięczny cel w PLN, wykres przepracowanych godzin, ile brakuje do celu.

### 🏗️ Architektura

```
┌─────────────────────────────────────────────────────┐
│                     UI Layer                        │
│   ScheduleFragment · FinanceFragment · Widget       │
└──────────────────────┬──────────────────────────────┘
                       │ observe (LiveData)
┌──────────────────────▼──────────────────────────────┐
│                  ViewModel Layer                    │
│              MainViewModel (MVVM)                   │
└────────┬─────────────┬──────────────────┬───────────┘
         │             │                  │
┌────────▼──┐  ┌───────▼──────┐  ┌───────▼────────────┐
│  Room DB  │  │  Gmail API   │  │   AlarmScheduler   │
│ (SQLite)  │  │  + Retrofit  │  │  (AlarmManager)    │
└────────┬──┘  └───────┬──────┘  └───────┬────────────┘
         │             │                  │
┌────────▼─────────────▼──────────────────▼────────────┐
│              Data / Background Layer                 │
│  ExcelParsingService · ShiftRepository · Receivers  │
└──────────────────────────────────────────────────────┘
```

### 🛠️ Tech Stack

| Warstwa | Technologia |
|---------|------------|
| Język | Java |
| Architektura | MVVM + Repository Pattern |
| Baza danych | Room (SQLite), custom migrations |
| Sieć & Auth | Retrofit, Gson, Gmail API, OAuth 2.0 |
| Android API | AlarmManager, BroadcastReceivers, Full-Screen Intents |
| UI | Material Design 3, Glassmorphism, ViewPager2, MPAndroidChart |
| Testy | JUnit 4 (18 unit testów logiki biznesowej) |

### 🧪 Testy

Projekt ma pokrycie testami dla krytycznej logiki biznesowej:
```bash
./gradlew testDebugUnitTest
# BUILD SUCCESSFUL — 18 testów (ShiftUtils: overlap, nocne, finanse, widget)
```

### 🗺️ Plany

*   **System Zastępstw** — UI do ręcznego dodawania/zarządzania współpracownikami na zmianie.
*   **Dyspo-Bot** — automatyczne wypełnianie formularzy dyspozycyjności (WebView + JS injection).
*   **Strażnik BHP** — detekcja nielegalnych grafikowych „clopenów" (zamknięcie + otwarcie bez 11h przerwy).
*   **Photo Parser (Killer Feature)** — zdjęcie kartki z grafikiem → automatyczne dodanie zmian (ML Kit OCR lub Gemini Vision API).
*   **Public Release / White-label** — otwarta architektura parserów (Excel/PDF/CSV/OCR), onboarding dla innych branż.

---

## 🇬🇧 About the Project

Hi! I'm Kacper.

Cinema schedules are chaotic — shifts arrive as Excel attachments, often last-minute, and many end deep in the night. Instead of manually copying everything to a calendar and tracking salary in a spreadsheet, I built an Android app that handles it all automatically. It's been my daily driver for months.

This is **not** a tutorial copy-paste project. It's a battle-tested native app solving real operational problems I face every week.

### 🔥 Engineering Highlights

*   **Gmail & Excel Automation** — integrates with the Gmail API (OAuth 2.0) to fetch and parse `.xlsx` schedule attachments in the background using Apache POI.
*   **SSoT Sync Engine** — a smart data-merge algorithm prevents automated Excel imports from overwriting manual edits (shift covers, hour corrections). Each manually touched record gets a protection flag (`isManuallyEdited`).
*   **Bulletproof Alarm System — device-tested** — custom exact-timing alarm system tested on Samsung S24 FE (Android 16 / One UI 8.5):
    *   Full-Screen Intent wakes device from deep sleep over the lockscreen.
    *   Handles night shifts crossing midnight.
    *   Reliable snooze (+10 min exact alarm).
    *   Survives device reboots via `BOOT_COMPLETED` receiver.
    *   Auto-cancels & reschedules when shift times are edited.
*   **Crew Intersection Engine** — custom interval-overlap algorithm that instantly shows who you're sharing a shift with.
*   **Financial Dashboard** — monthly salary goal tracker with dynamic charts.

### 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                     UI Layer                        │
│   ScheduleFragment · FinanceFragment · Widget       │
└──────────────────────┬──────────────────────────────┘
                       │ observe (LiveData)
┌──────────────────────▼──────────────────────────────┐
│                  ViewModel Layer                    │
│              MainViewModel (MVVM)                   │
└────────┬─────────────┬──────────────────┬───────────┘
         │             │                  │
┌────────▼──┐  ┌───────▼──────┐  ┌───────▼────────────┐
│  Room DB  │  │  Gmail API   │  │   AlarmScheduler   │
│ (SQLite)  │  │  + Retrofit  │  │  (AlarmManager)    │
└────────┬──┘  └───────┬──────┘  └───────┬────────────┘
         │             │                  │
┌────────▼─────────────▼──────────────────▼────────────┐
│              Data / Background Layer                 │
│  ExcelParsingService · ShiftRepository · Receivers  │
└──────────────────────────────────────────────────────┘
```

### 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java |
| Architecture | MVVM + Repository Pattern |
| Database | Room (SQLite), custom schema migrations |
| Networking & Auth | Retrofit, Gson, Gmail API, OAuth 2.0 |
| Android APIs | AlarmManager, BroadcastReceivers, Full-Screen Intents |
| UI | Material Design 3, Glassmorphism, ViewPager2, MPAndroidChart |
| Testing | JUnit 4 (18 unit tests covering core business logic) |

### 🧪 Tests

```bash
./gradlew testDebugUnitTest
# BUILD SUCCESSFUL — 18 tests (ShiftUtils: interval overlaps, night shifts, payroll, widget)
```

### 🗺️ Roadmap

*   **Replacement System** — UI for manually managing co-worker swaps per shift.
*   **Dyspo-Bot** — automated availability form filling (WebView + JS injection into MS Forms).
*   **OHS Guardian** — detects illegal "Clopen" shifts (close + open without 11h rest break).
*   **Photo Parser (Killer Feature)** — snap a photo of a paper schedule → shifts auto-imported (ML Kit OCR or Gemini Vision API).
*   **Public / White-label Release** — open parser architecture (Excel/PDF/CSV/OCR), full onboarding for other industries.

---

### ⚙️ Local Setup

To run this project locally you'll need to configure your own Google Cloud credentials:

1. Clone the repository.
2. Create a project in [Google Cloud Console](https://console.cloud.google.com/).
3. Enable the **Gmail API** and set up the **OAuth Consent Screen**.
4. Generate an OAuth 2.0 Android Client ID and save the config as `credentials.json` (already in `.gitignore`).
5. Create `keystore.properties` in the root directory with your signing config (template in the repo, already in `.gitignore`).
6. Open in Android Studio and run.

### 📄 License

MIT — see [LICENSE](LICENSE) file.