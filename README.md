# KinoBot (Cinema Assistant) 🎬🍿

> **Cześć! / Hi! 👋**  
> *Scroll down for the English version.*

---

## 🇵🇱 O projekcie (Dla Rekrutera)

Nazywam się Kacper i zbudowałem KinoBota. 
Jeśli przeglądasz ten kod, pewnie zastanawiasz się, po co kolejna aplikacja do grafiku. Odpowiedź jest prosta: grafiki w kinie to chaos. Zmiany wpadają mailem w Excelu, często na ostatnią chwilę, a praca nierzadko kończy się w środku nocy (tzw. "zamki"). Zamiast ręcznie przepisywać to do kalendarza i liczyć godziny, napisałem aplikację, która robi to za mnie.

To **nie jest** zabawkowy projekt z tutoriala, który ładnie wygląda na GitHubie, a w rzeczywistości wywala się przy pierwszym błędzie. KinoBot to przetestowana w boju, natywna aplikacja na Androida, która od miesięcy jest moim głównym narzędziem codziennej pracy. Rozwiązuje realne problemy moje i moich współpracowników.

### 🔥 Co tu zbudowałem (Główne Funkcje)

*   **Automatyzacja Gmail & Excel:** Apka łączy się z Gmailem (OAuth 2.0), nasłuchuje na maile z grafikiem, po cichu pobiera załączniki `.xlsx` i parsuje je w tle (Apache POI). Magia dzieje się sama.
*   **Architektura SSoT (Single Source of Truth):** Inteligentny mechanizm łączenia danych. Kiedy biorę za kogoś zastępstwo lub ręcznie zmieniam godziny, aplikacja oznacza te rekordy jako "nietykalne". Dzięki temu kolejny automatyczny import z Excela nie nadpisze moich ręcznych modyfikacji.
*   **Kuloodporny, pełnoekranowy budzik:** System alarmów napisany od zera na `AlarmManager`.
    *   Wie, czy zmiana jest w dzień, czy w nocy (obsługuje przejścia przez północ).
    *   Dynamicznie anuluje i przebudowuje alarmy, gdy edytuję grafik.
    *   Odpala pełnoekranowy widok (Full-Screen Intent) wybudzając urządzenie z trybu uśpienia.
    *   Pamięta ustawienia po restarcie telefonu (`BOOT_COMPLETED`).
    *   Wspiera drzemki i wibruje, żeby mnie dobudzić.
*   **Z kim dzisiaj pracuję?:** Moduł przecinania przedziałów czasowych. Aplikacja w mgnieniu oka analizuje cały grafik kina i pokazuje mi na widżecie (lub w apce) listę osób, które będą ze mną na zmianie.
*   **Kalkulator Finansów:** Śledzi mój cel finansowy, liczy przepracowane godziny i pokazuje (na wykresach), ile brakuje mi do celu.

### 🛠️ Tech Stack
*   **Język:** Java
*   **Architektura:** MVVM (Model-View-ViewModel) z poprawnym zarządzaniem cyklem życia
*   **Baza Danych:** Room (SQLite) - solidne relacje, niestandardowe migracje w kodzie
*   **Sieć & Autoryzacja:** Retrofit, Gson, Google Play Services Auth (OAuth2), Gmail API
*   **Android API:** AlarmManager, BroadcastReceivers, Full-Screen Intents, Services
*   **UI/UX:** Material Design 3, Glassmorphism, ViewPager2, MPAndroidChart

### 🗺️ Plany na przyszłość (Roadmapa)
Projekt cały czas żyje. W planach mam:
*   **Dyspo-Bot:** Moduł automatyzujący podawanie dyspozycyjności. Genereowanie odpowiedniego tekstu lub wstrzykiwanie JS do WebView, by auto-uzupełniać uczelniany/firmowy formularz (np. MS Forms).
*   **Strażnik BHP:** Detekcja "patologii grafikowych". Aplikacja wyrzuci alert, gdy menedżer wpisze mi "zamknięcie", a na drugi dzień "otwarcie" (Clopen), łamiąc 11-godzinną przerwę na odpoczynek.
*   **Public Release / White-label:** Uwolnienie parsera. Planuję oddzielić logikę czytania Excela za interfejsami, by móc podpinać inne rodzaje grafików (np. PDF, CSV) i wypuścić aplikację dla pracowników innych sieci i branż.

---

## 🇬🇧 About the Project (For Recruiters)

Hi! I'm Kacper, the creator of KinoBot.
If you're looking at this code, you might be asking yourself: "why build another calendar/scheduling app?". The answer is simple: cinema schedules are chaotic. Shifts arrive randomly as Excel attachments via email, are updated last-minute, and often end deep in the night. Instead of wasting time manually copying shifts to Google Calendar and tracking my salary in spreadsheets, I built a system to automate the pain away.

This is **not** a copy-paste tutorial app. It's a robust, battle-tested native Android application that actually solves real-world operational problems and has been my daily driver for months.

### 🔥 Engineering Highlights & Core Features

*   **Seamless Email & Excel Pipeline:** Integrates deeply with the **Gmail API** via OAuth 2.0. A background service fetches new emails, extracts `.xlsx` attachments, and parses the entire cinema schedule using Apache POI.
*   **Single Source of Truth (SSoT) Sync:** Cinema shifts often change (e.g., co-worker swaps). The app features a smart data-merge algorithm that protects manually edited or swapped shifts from being blindly overwritten by subsequent automated Excel syncs.
*   **Bulletproof Alarm System:** A custom, exact-timing alarm system built on `AlarmManager`.
    *   Handles night shifts crossing midnight gracefully.
    *   Uses `BroadcastReceivers` and Full-Screen Intents to wake up the device and show a custom alarm screen over the lockguard.
    *   Automatically cleans up and reschedules alarms when shift times are edited.
    *   Survives device reboots and system memory trims.
    *   Features a reliable snooze mechanism.
*   **Crew Intersection Engine:** Want to know who you're working with? A custom algorithm calculates time-interval overlaps from the global schedule to instantly show you your co-workers for the day on the app's widget.
*   **Financial Dashboard:** Tracks monthly hour quotas and salary goals with a dynamic glassmorphic UI and interactive charts.

### 🛠️ Tech Stack
*   **Language:** Java
*   **Architecture:** MVVM (Model-View-ViewModel) ensuring separation of concerns
*   **Local Persistence:** Room Database (SQLite) with complex schema migrations and data relations
*   **Networking & Auth:** Retrofit, Gson, Google Play Services Auth (OAuth2), Gmail API
*   **Android APIs:** AlarmManager, BroadcastReceivers, Intents, Background Processing
*   **UI/UX:** Material Design 3, ViewPager2, MPAndroidChart, Glassmorphism aesthetic

### 🗺️ Future Roadmap
The app is under active development. Upcoming features:
*   **Dyspo-Bot:** Automating availability submissions. The app will generate availability schemas and potentially auto-fill Microsoft Forms via WebView JS injection.
*   **OHS Guardian (BHP):** Algorithmic schedule validation to warn the user if the manager assigns an illegal "Clopen" shift (closing late night + opening early morning), violating the statutory 11-hour rest period.
*   **White-label Release:** Refactoring the parsing logic behind interfaces to easily swap in `PdfScheduleParser` or `CsvScheduleParser`, aiming to release the app publicly for workers across different industries.

---

### ⚙️ Local Setup
To run this project locally:
1. Clone the repository.
2. Create a project in the [Google Cloud Console](https://console.cloud.google.com/).
3. Enable the **Gmail API** and configure the **OAuth Consent Screen**.
4. Generate an OAuth 2.0 Client ID for Android and download the `credentials.json` file.
5. Create a `keystore.properties` file in the root directory for signing configs.
6. Build and run via Android Studio.