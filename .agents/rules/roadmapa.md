---
trigger: always_on
---

# Roadmapa KinoBot v2.9 → v3.5

## Faza 0 — Stabilizacja techniczna / Release Hygiene
Status: ✅ DONE
Cel: Projekt ma się budować, być bezpieczny i gotowy pod dalszy rozwój.

### Zadania:
- [x] Nadać `gradlew` prawa wykonywania. ✅ (commit b53f38d)
- [x] Zweryfikować wersję Gradle Wrapper i Android Gradle Plugin. ✅ (Gradle 9.3.1, Foojay usunięty)
- [x] Doprowadzić do działania `./gradlew testDebugUnitTest`. ✅ (0 testów, ale pipeline działa)
- [x] Doprowadzić do działania `./gradlew assembleDebug`. ✅ (BUILD SUCCESSFUL in 6s)
- [ ] Dodać CI build check.
- [ ] Dodać basic secret scan.
- [x] Dopisać do `.gitignore`: `*.pem`, `*.key`, `*.p12`, `*.der`. ✅
- [ ] Rozważyć rotację hasła/keystore, jeśli stare dane były kiedykolwiek publiczne.
- [x] Usunąć sekrety z `app/build.gradle` → `keystore.properties`. ✅ (commit b53f38d)
- [x] `exported="false"` dla prywatnych komponentów alarmu. ✅ (commit b53f38d)

## Faza 1 — Sprint 3: Smart Automation & UI
Status: ✅ DONE
Cel: Domknąć codzienne funkcje użytkownika: ekipa, zastępstwa, budzik, widget.

### Feature A.6.1 — System Zastępstw
- [x] Dopracować przycisk `[+ Dodaj osobę]`. ✅ (istniał, podpięty handler)
- [x] Dodać walidację imienia, daty, godzin i kategorii. ✅ (regex HH:mm + empty check)
- [x] Zapisywać nowego współpracownika jako `GlobalShift`. ✅
- [x] Wymuszać `isManuallyEdited = true`. ✅
- [x] Chronić ręcznie dodaną osobę przed parserem Excela. ✅ (przez isManuallyEdited)
- [x] Zapobiegać duplikatom. ✅ (Room UNIQUE + UI feedback z Toast)
- [x] Pozwalać edytować dodaną osobę. ✅ (showCoworkerEditDialog)
- [x] Pozwalać soft-delete osoby. ✅ (deleteGlobalShift w edit dialog)
- [x] Odświeżać widget po dodaniu/edycji/usunięciu. ✅ (triggerUpdate w insertGlobalShift)

### Feature B — Inteligentny Budzik
- [x] Ustabilizować alarm dla zmian z Excela. ✅ (AlarmScheduler + null guard)
- [x] Ustabilizować alarm dla zmian ręcznych. ✅ (upsert w toggleAlarmForShift)
- [x] Zmienić `toggleAlarmForShift(date, startTime, ...)` na pełne dane zmiany. ✅
- [x] Nie tworzyć `GlobalShift` z pustym `endTime`. ✅ (fallback: endTime = startTime)
- [x] Anulować stary alarm po edycji godziny. ✅ (cancelAlarm przed re-schedule)
- [x] Przetestować alarm po restarcie telefonu. ✅ (BootAlarmReceiver)
- [x] Drzemka (snooze) przetestowana i działa. ✅ (Samsung S24 FE, Android 16)
- [x] Dodać checklistę manual testów alarmu.
- [x] Hall of Fame Easter Egg: Toast z odliczaniem kliknięć + setCancelable(false). ✅ (commit 7e81330)

### Feature C — Widget 2.0 (duży widget, główny)
- [x] Dodać countdown do najbliższej zmiany. ✅
- [x] Dodać status "trwa teraz". ✅
- [x] Pokazać ikonę aktywnego alarmu. ✅
- [x] Pokazać współpracowników bez użytkownika samego siebie. ✅
- [x] Odświeżać widget po syncu, edycji zmiany i zmianie alarmu. ✅
- [x] **BUG FIX:** Filtrować zmiany z flagą `isReplacement=true` (zmiana oddana) — nie pojawiają się w widgecie ani nie liczą się do finansów. ✅

### Feature D — Widget Stack 2x2 (mały widget — stos kart)
- [x] Nowy widget `ShiftStackWidgetProvider` — stos przewijanych kart. ✅ (commit 7e81330)
- [x] Każda karta: data, godziny (🌙 zamknięcia), stanowisko (chip), dzwonek, ekipa. ✅
- [x] Filtrowanie `isReplacement=true` i przeszłych dat. ✅
- [x] Deep-link: kliknięcie w kartę otwiera zakładkę Grafik z dialogiem zmiany. ✅
- [x] Powiększone czcionki i paddingi (18sp/15sp/14sp). ✅ (commit 7e81330)

## Faza 2 — Quality & Data Safety
Status: ✅ DONE
Cel: Aplikacja nie traci danych i ma testy krytycznej logiki.

### Zadania:
- [x] Dodać testy `ShiftUtils`. ✅ (obecne i uaktualnione w pakiecie testowym)
- [x] Dodać testy overlapów nocnych. ✅ (zweryfikowane rozbiciem nocnych interwałów od 17 do 01)
- [x] Dodać testy payroll. ✅ (PayrollCalculator.java oraz PayrollCalculatorTest z nadgodzinami)
- [x] Dodać testy parsera Excela. ✅ (NewFormatExcelParserTest — uzdrowienie wyławiania opisu TMS, 34/34 zaliczone)
- [x] Dodać testy migracji Room. ✅ (spójny polimer spajający testową instancję w RoomDatabaseTest na v13)
- [x] Usunąć lub ograniczyć `fallbackToDestructiveMigration()`. ✅ (ograniczono rygorem od wersji 1 do 2)
- [x] Włączyć `exportSchema = true`. ✅ (dane z eksportu architektonicznie opisane do walidacji na bazie v13)
- [x] Dodać backup/export JSON. ✅ (BackupManager — zrzucenie grafiku w Storage Access Framework)
- [x] Dodać import backupu. ✅ (BackupManager + ProfileFragment ze sprawnym odcinaczem dublatury i oknem raportowym)

## Faza 3 — Dyspo-Bot
Status: PLANOWANE
Cel: Automatyzacja dyspozycji.

### MVP:
- [ ] Ekran konfiguracji dyspozycji.
- [ ] Szablony dostępności.
- [ ] Generator tekstu dyspozycji.
- [ ] Kopiowanie do schowka.
- [ ] Link do Microsoft Forms.

### Advanced:
- [ ] WebView z formularzem.
- [ ] JS Injection do wypełniania pól.
- [ ] Tryb preview przed wysłaniem.
- [ ] Tryb awaryjny copy-paste.
- [ ] Logika bezpieczeństwa: nigdy nie wysyłać bez potwierdzenia użytkownika.

## Faza 4 — Finanse Pro
Status: PLANOWANE
Cel: Aplikacja staje się realnym kalkulatorem celu finansowego.

### Zadania:
- [ ] Cel miesięczny w PLN.
- [ ] ProgressBar celu finansowego.
- [ ] Ile godzin brakuje do celu.
- [ ] Symulacja: "jeśli weźmiesz 2 dodatkowe zmiany".
- [ ] Historia wypłat.
- [ ] Eksport CSV/PDF.
- [ ] Oddzielić logikę payroll od UI/ViewModel.

## Faza 5 — Strażnik BHP
Status: PLANOWANE
Cel: Alerty o złym grafiku.

### Zadania:
- [ ] Clopen Alert: przerwa < 11h.
- [ ] Alert po zamku i porannym openie.
- [ ] Alert o zbyt długim maratonie.
- [ ] Alert o zbyt wielu godzinach w tygodniu.
- [ ] Dashboard z alertami.
- [ ] Możliwość oznaczenia alertu jako przeczytany.

## Faza 6 — KinoBot Wrapped
Status: PLANOWANE
Cel: Statystyki miesięczne/roczne.

### Statystyki:
- [ ] Liczba godzin.
- [ ] Liczba zamków.
- [ ] Liczba openów.
- [ ] Najdłuższy maraton.
- [ ] Najczęstszy współpracownik.
- [ ] Najczęstsze stanowisko.
- [ ] Najlepszy miesiąc finansowo.
- [ ] Najgorszy miesiąc finansowo.
- [ ] Share/screenshot.

## Faza 7 — Public Release / White-label
Status: FUTURE
Cel: Aplikacja działa nie tylko dla jednego kina.

### Zadania (Architektura Universal Parsers):
- [ ] Stworzenie głównego interfejsu `ScheduleParser` (odcięcie logiki biznesowej od tego, skąd biorą się dane).
- [ ] **W pełni dynamiczny parser algorytmiczny (Koncepcja):**
  - Aplikacja dynamicznie skanuje wiersze i kolumny.
  - Wyszukiwanie dat na podstawie wbudowanych regexów.
  - Wykrywanie wierszy/kolumn z imionami na podstawie lokalnej bazy (np. bazy polskich imion) z założeniem, że jeśli w danym rzędzie padło kilka znanych imion, to pozostałe nieznane słowa (w tym zagraniczne imiona) też mogą być imionami.
  - Automatyczne szukanie stanowisk powiązanych z tymi imionami (wyżej, obok).
- [ ] **Wizualny Kreator Szablonów (Dla Excel / PDF / CSV):** Ekran, na którym użytkownik z innej firmy wgrywa swój plik, widzi go w formie siatki i klika palcem: "Ten wiersz to daty, ta kolumna to moje imię". Apka zapisuje to jako lokalny szablon (likwiduje to problem sztywnych hardcode'ów bez używania drogiego AI).
- [ ] **Photo & OCR Parser (Killer Feature):**
  - **Opcja A (ML Kit Vision):** Analiza zdjęcia grafiku z tablicy korkowej na urządzeniu (wymaga mapowania koordynatów tekstu).
  - **Opcja B (AI Cloud Vision / Gemini):** Najprostsze dla usera: robi zdjęcie pogiętej kartki, wysyła do AI, dostaje gotowe zmiany wpisane w kalendarz. (Wymaga darmowego API key lub modelu w chmurze).
- [ ] Implementacja `ExcelScheduleParser` (dynamiczny, na podstawie Wizualnego Kreatora / Algorytmiczny).
- [ ] Implementacja `PdfScheduleParser`.
- [ ] Implementacja `CsvScheduleParser`.
- [ ] Konfiguracja źródła grafiku.
- [ ] Konfiguracja nazwy użytkownika.
- [ ] Konfiguracja mail filter.
- [ ] Dynamic Theme / Material You.
- [ ] Onboarding.
- [ ] Backup danych.
- [ ] Privacy screen.