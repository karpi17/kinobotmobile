# KinoBot 🎬🍿 — v3.5

> **Cześć! / Hi! 👋** — *Scroll down for the English version.*

![Platform](https://img.shields.io/badge/platform-Android-green?logo=android)
![Language](https://img.shields.io/badge/language-Java-orange?logo=java)
![Version](https://img.shields.io/badge/version-v3.5-blue)
![Min SDK](https://img.shields.io/badge/minSdk-26-blue)
![Target SDK](https://img.shields.io/badge/targetSdk-35-blue)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](./LICENSE)
![Status](https://img.shields.io/badge/status-Production%20Ready-brightgreen)
![Tests](https://img.shields.io/badge/tests-50%2B%20passing-brightgreen)

---

## 🇵🇱 O projekcie

Nazywam się **Kacper Wernerowicz** i zbudowałem **KinoBota**.

Grafiki pracy w kinach, gastronomii i retailu to codzienny chaos. Zmiany wpadają mailem w zawiłych arkuszach Excela albo wiszą jako kartki na tablicy korkowej, nierzadko kończąc się głęboko w nocy (tzw. „zamki" do 01:00). Zamiast ręcznie przepisywać to do kalendarza, szukać zastępstw na grupach na Messengerze i liczyć pensję na kalkulatorze, napisałem aplikację, która automatyzuje cały proces — od odbioru grafiku po rozliczenie finansowe.

Aplikacja została zbudowana od zera jako w pełni natywne rozwiązanie na Androida w architekturze MVVM z relacyjną bazą Room (SSoT), zaprojektowane z myślą o niezawodności i obsłudze niestandardowych danych w codziennym środowisku pracy.

---

### 🔥 Główne moduły aplikacji (v3.5)

#### 1. Wieloźródłowy Import Grafików (Excel & ML Kit OCR)
* **Auto-detekcja formatów Excela** — obsługa tradycyjnych grafików pracowniczych oraz rozbudowanych grafików biurowych/kierowniczych z podziałem na stanowiska (BAR, OBSŁUGA WIDZA, SP, ZAMEK).
* **Photo & OCR Parser (ML Kit Vision)** — zrób zdjęcie wydrukowanego grafiku z tablicy: aplikacja poprawia kontrast, grupuje tekst we współrzędnych przestrzennych i interpretuje wpisy w locie.
* **Podgląd importu przed zapisem** — ekran inspekcji z możliwością ręcznej korekty godzin przed zatwierdzeniem do bazy.
* **Architektura SSoT z ochroną zmian ręcznych** — gdy wymienisz się zmianą ze współpracownikiem (`isManuallyEdited = true`) lub oddasz zmianę (`isReplacement = true`), kolejny automatyczny import z maila nie zniszczy Twoich ustaleń.

#### 2. Strażnik BHP (OHS Guardian)
* **Wykrywanie naruszeń odpoczynku dobowego (tzw. Clopen < 11h)** — inteligentny algorytm czasu rzeczywistego uwzględniający zmiany nocne przechodzące przez północ (np. zamek `17:00–01:00` i otwarcie `09:00–17:00` kolejnego dnia) oraz przełomy miesięcy (np. 31 sierpnia $\rightarrow$ 1 września).
* **Wykrywanie maratonów** — ostrzeżenia przed pracą przez więcej niż 6 dni roboczych z rzędu bez dnia wolnego.
* **Limit 48h tygodniowo** — kontrola łącznego wymiaru czasu pracy w oparciu o kalendarz ISO.

#### 3. Inteligentny Szukacz Zastępstw (`ReplacementFinder`)
* Koniec z pytaniem w próżnię na Messengerze *„Kto weźmie piątek?”*.
* Algorytm analizuje grafik całej ekipy w dniach $D-1$, $D$ oraz $D+1$ i wskazuje tylko te osoby, które **mają w dany dzień wolne i wzięcie tej zmiany nie złamie im doby pracowniczej (11h BHP)**.
* **1-Click Messenger** — generuje i kopiuje do schowka gotową, sformatowaną wiadomość na grupę z datą, godzinami i stanowiskiem.

#### 4. Finanse Pro (Wersjonowanie stawek & Godziny nocne)
* **Historia stawek (`RateHistory`)** — obsługa podwyżek w trakcie miesiąca z gwarancją unikalności stawki per dzień.
* **Godziny nocne (`22:00–06:00`)** — automatyczne wyliczanie godzin nocnych (np. 3h na każdym zamku `17:00–01:00`).
* **Kalkulator celu i symulator** — pasek postępu celu w PLN, wyliczanie brakujących godzin i symulacja dodatkowych zmian.
* **Zarządzanie Stratami i Napiwkami** — ewidencja potrąceń i dodatków z możliwością przeglądania i usuwania wpisów.

#### 5. Współpracownicy i Tryb Kierownika
* **Oznaczenia ☀️ OPEN / 🌙 ZAMEK** — dynamiczne wyznaczanie pierwszej zmiany dnia w danym dziale oraz zmian nocnych.
* **Tryb Kierownika w Kalendarzu** — przełącznik `[Współpracownicy na mojej zmianie]` $\leftrightarrow$ `[Cała obsada dnia]`. W dni wolne użytkownik może sprawdzić pełną obsadę kina posortowaną godzinowo.

#### 6. KinoBot Wrapped (Roczne Podsumowanie)
* Statystyki roczne i „all-time”: przepracowane godziny, zliczone Openy i Zamki, najdłuższy maraton, najczęstsze stanowisko oraz „Zgrana paka” (najczęstszy współpracownik).
* Wyznaczanie najlepszego i najgorszego miesiąca finansowo.
* **Udostępnianie zrzutu ekranu** — generowanie grafiki podsumowania dopasowanej do ciemnego motywu do wysłania znajomym.

#### 7. Niezawodny Budzik i Widgety 2.0
* **Pełnoekranowy budzik (`Full-Screen Intent`)** — wybudza ekran zablokowanego telefonu, obsługuje drzemki, przeżywa restart telefonu (`BOOT_COMPLETED`) i automatycznie anuluje się przy edycji lub oddaniu zmiany.
* **Dwa widgety na pulpit** — duży widget 2.0 z odliczaniem i statusem oraz widget 2x2 w formie przewijanego stosu kart (`ShiftStackWidgetProvider`) z deep-linkiem do grafiku.
* **Pełny Backup JSON (1-Click Restore)** — eksport całego grafiku, współpracowników, historii stawek i ustawień profilu do jednego pliku.

---

### 🏗️ Architektura Systemu

```
┌────────────────────────────────────────────────────────────────────────┐
│                                UI Layer                                │
│  DashboardFragment · ScheduleFragment · FinanceFragment · Statistics   │
│         ImportPreviewFragment · ProfileFragment · 2x App Widgets       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ observe (LiveData)
┌───────────────────────────────────▼────────────────────────────────────┐
│                             ViewModel Layer                            │
│                  MainViewModel (MVVM / MediatorLiveData)               │
└──────────┬────────────────────────┬───────────────────────┬────────────┘
           │                        │                       │
┌──────────▼────────┐     ┌─────────▼────────┐     ┌────────▼────────────┐
│    Room Database  │     │   Domain Logic   │     │   System Services   │
│   shifts          │     │ BhpGuard         │     │ AlarmScheduler      │
│   global_shifts   │     │ PayrollCalculator│     │ NotificationSchedule│
│   rate_history    │     │ ReplacementFinder│     │ AppWidgetManager    │
│   losses / tips   │     │ StatisticsCalc   │     │ FileProvider        │
└──────────┬────────┘     └─────────┬────────┘     └────────┬────────────┘
           │                        │                       │
┌──────────▼────────────────────────▼───────────────────────▼────────────┐
│                        Data & Parser Layer                             │
│  AutoDetectExcelParser · NewFormatExcelParser · OcrScheduleParser     │
│       GmailApiClient · BackupManager · BootAlarmReceiver               │
└────────────────────────────────────────────────────────────────────────┘
```

---

### 🛠️ Tech Stack

| Warstwa | Technologie |
|---------|-------------|
| **Język** | Java 17 |
| **Architektura** | MVVM, Repository Pattern, Single Source of Truth (SSoT) |
| **Baza danych** | Room Database (SQLite), 15 migracji schematu, indeksy unikalne |
| **Wizja & OCR** | Google ML Kit Text Recognition |
| **Formaty plików** | Apache POI (Excel `.xlsx` / `.xls`), JSON Backup |
| **Sieć & API** | Retrofit 2, OkHttp 3, Gson, Gmail API, OAuth 2.0 |
| **Komponenty Androida** | AlarmManager (Exact Alarms), BroadcastReceivers, RemoteViewsFactory, FileProvider |
| **Interfejs** | Material Design 3, Dark Theme, CoordinatorLayout, ViewPager2, MPAndroidChart |
| **Testy** | JUnit 4, AndroidX Test, Room In-Memory Testing |

---

### 🧪 Testy Jednostkowe i Integracyjne

Wszystkie krytyczne reguły domenowe są objęte automatycznymi testami:
```bash
./gradlew testDebugUnitTest
```
**Status: 100% PASS (50+ testów)**
- `BhpGuardTest` — detekcja nocnych clopenów (`17:00–01:00` $\rightarrow$ `09:00–17:00`), maratonów i limitów tygodniowych.
- `PayrollCalculatorTest` — obliczanie stawek per zmiana, podwyżki w trakcie miesiąca, godziny nocne (`22:00–06:00`).
- `ReplacementFinderTest` — wyszukiwanie kandydatów, wykluczanie clopenów i formatowanie wiadomości.
- `StatisticsCalculatorTest` — roczne agregacje, detekcja openów per stanowisko, wykluczanie własnego imienia.
- `ExcelParserIntegrationTest` & `NewFormatExcelParserTest` — parsowanie zawiłych formatów Excela.
- `RoomDatabaseTest` — operacje DAO i spójność bazy.

### 📄 Licencja

Projekt udostępniany na licencji MIT — pełny tekst licencji znajduje się w pliku [LICENSE](./LICENSE).

---

## 🇬🇧 About the Project

I'm **Kacper Wernerowicz**, and I built **KinoBot**.

Cinema, hospitality, and retail scheduling is notoriously chaotic. Shifts are sent via complex spreadsheets or posted on corkboards, frequently finishing late at night (past midnight "closing" shifts). Instead of manually transcribing hours into a calendar, asking coworkers for shift swaps on messaging groups, and recalculating pay in spreadsheets, I engineered an Android application that automates the entire lifecycle — from schedule ingestion to payroll audit.

Built from the ground up as a native Android application following the MVVM architecture and Room Database (SSoT), KinoBot is designed for high reliability and operational resilience when working with messy, real-world scheduling data.

---

### 🔥 Key Engineering Highlights (v3.5)

* **Multi-Source Schedule Ingestion (Excel & ML Kit OCR)** — auto-detects schedule layouts (staff vs. office formats) using Apache POI, and extracts shifts from corkboard photos via Google ML Kit Text Recognition with image contrast preprocessing.
* **SSoT Conflict Resolution** — preserves manually edited shifts and shift swaps (`isManuallyEdited`) across subsequent automated imports.
* **OHS Guardian (`BhpGuard`)** — monitors rest break violations (Clopen shifts < 11h) including overnight shifts crossing midnight (`17:00–01:00` $\rightarrow$ `09:00–17:00`) and month boundaries, marathon warnings (>6 consecutive days), and 48h weekly limits.
* **Smart Replacement Finder** — queries the collective `GlobalShift` roster across days $D-1$, $D$, and $D+1$ to find coworkers who are off-duty and can cover without violating OHS rest rules, with 1-click clipboard formatting for Messenger.
* **Payroll Pro with Rate History** — tracks hourly wage increases mid-month with strict date-effective uniqueness, computes overnight hours (`22:00–06:00`), calculates monthly goal progress, and manages tips and losses.
* **Role Badges & Manager Day View** — automatically flags ☀️ **OPEN** (earliest shift per station) and 🌙 **CLOSE** shifts, and enables team leads to review full daily cinema staffing even on days off.
* **KinoBot Wrapped** — yearly performance and crew retrospectives with dark-theme screenshot sharing.
* **Exact Alarms & Stack Widgets** — full-screen lockscreen alarm engine (`AlarmManager`), home screen widgets (including a 2x2 card stack), and 1-click JSON backup/restore.

---

### ⚙️ Local Setup

```bash
git clone https://github.com/karpi17/kinobotmobile.git
cd kinobotmobile
./gradlew testDebugUnitTest assembleDebug
```

### 📄 License

Distributed under the MIT License. See [LICENSE](./LICENSE) for details.
