package com.asystent.kinowy.parsers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Shift;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parser zdjęć grafików ze skanera OCR (ML Kit).
 */
public class OcrScheduleParser implements ScheduleParser {

    private static final String TAG = "OcrScheduleParser";

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase();
        return lower.contains("image") || lower.contains("jpg") || lower.contains("jpeg") || lower.contains("png");
    }

    @Override
    public ScheduleParseResult parse(InputStream inputStream, ScheduleParseOptions options) throws Exception {
        Log.d(TAG, "Parsowanie obrazu OCR...");
        List<ParserWarning> warnings = new ArrayList<>();
        warnings.add(new ParserWarning(ParserWarning.Type.LOW_CONFIDENCE,
                "Skaner OCR wyciągnął dane najlepiej jak potrafił. Proszę dokładnie sprawdzić i ewentualnie poprawić godziny zmian w podglądzie."));

        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
        if (bitmap == null) {
            throw new IllegalArgumentException("Nie można odczytać obrazu z podanego pliku.");
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        
        Text result = Tasks.await(recognizer.process(image));

        // 1. Zbieranie wszystkich linii tekstu
        List<Text.Line> allLines = new ArrayList<>();
        for (Text.TextBlock block : result.getTextBlocks()) {
            allLines.addAll(block.getLines());
        }

        Log.d(TAG, "OCR wyodrębnił " + allLines.size() + " linii z obrazu (wymiary: " + bitmap.getWidth() + "x" + bitmap.getHeight() + "):");
        for (int idx = 0; idx < allLines.size(); idx++) {
            Log.d(TAG, "  [OCR Line " + idx + "] '" + allLines.get(idx).getText() + "' bbox: " + allLines.get(idx).getBoundingBox());
        }

        // 2. Sortowanie po Y (z góry na dół)
        allLines.sort((l1, l2) -> {
            if (l1.getBoundingBox() == null || l2.getBoundingBox() == null) return 0;
            return Integer.compare(l1.getBoundingBox().top, l2.getBoundingBox().top);
        });



        // 3. Grupowanie w rzędy
        List<List<Text.Line>> rows = new ArrayList<>();
        List<Text.Line> currentRow = new ArrayList<>();
        int currentRowCenterY = -1;

        for (Text.Line line : allLines) {
            if (line.getBoundingBox() == null) continue;
            int centerY = line.getBoundingBox().centerY();
            
            if (currentRow.isEmpty()) {
                currentRow.add(line);
                currentRowCenterY = centerY;
            } else {
                int height = line.getBoundingBox().height();
                if (Math.abs(centerY - currentRowCenterY) < height) {
                    currentRow.add(line);
                    // Aktualizuj środek rzędu
                    currentRowCenterY = (currentRowCenterY * (currentRow.size() - 1) + centerY) / currentRow.size();
                } else {
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                    currentRow.add(line);
                    currentRowCenterY = centerY;
                }
            }
        }
        if (!currentRow.isEmpty()) rows.add(currentRow);

        // 4. Sortowanie rzędów po X (od lewej do prawej)
        for (List<Text.Line> row : rows) {
            row.sort((l1, l2) -> {
                if (l1.getBoundingBox() == null || l2.getBoundingBox() == null) return 0;
                return Integer.compare(l1.getBoundingBox().left, l2.getBoundingBox().left);
            });
        }

        // --- Ekstrakcja danych (Podobna logika do NewFormatExcelParser) ---
        // Z racji niedoskonałości OCR, używamy NewFormatExcelParser.parseShiftText do parsowania tekstu.
        NewFormatExcelParser shiftParser = new NewFormatExcelParser();
        int currentYear = (options != null && options.getSelectedYear() > 0) ? options.getSelectedYear() : java.time.Year.now().getValue();
        int monthValue = java.time.LocalDate.now().getMonthValue(); 
        
        // Szukamy nazwy miesiąca w pierwszych rzędach OCR (zabezpieczenie na wypadek, gdyby miesiąc importu różnił się od bieżącego)
        for (int i = 0; i < Math.min(15, allLines.size()); i++) {
            String line = allLines.get(i).getText().toLowerCase();
            if (line.contains("jan")) { monthValue = 1; break; }
            else if (line.contains("feb")) { monthValue = 2; break; }
            else if (line.contains("mar")) { monthValue = 3; break; }
            else if (line.contains("apr")) { monthValue = 4; break; }
            else if (line.contains("may") || line.contains("maj")) { monthValue = 5; break; }
            else if (line.contains("jun")) { monthValue = 6; break; }
            else if (line.contains("jul") || line.contains("lip")) { monthValue = 7; break; }
            else if (line.contains("aug") || line.contains("sie")) { monthValue = 8; break; }
            else if (line.contains("sep")) { monthValue = 9; break; }
            else if (line.contains("oct")) { monthValue = 10; break; }
            else if (line.contains("nov")) { monthValue = 11; break; }
            else if (line.contains("dec")) { monthValue = 12; break; }
        }

        Map<String, List<Shift>> scheduleByName = new LinkedHashMap<>();
        List<String> allDates = new ArrayList<>();
        List<String> foundNames = new ArrayList<>();
        List<GlobalShift> allGlobalShifts = new ArrayList<>();
        List<Shift> targetUserShifts = new ArrayList<>();

        String targetUserName = options != null ? options.getTargetUserName() : "Kacper";
        String targetRole = options != null ? options.getTargetRole() : null;
        Text.Line targetUserElement = null;
        List<Text.Line> namesRow = null;

        // KROK 1: Szukamy wszystkich wystąpień imienia
        List<Text.Line> nameCandidates = new ArrayList<>();
        List<List<Text.Line>> rowCandidates = new ArrayList<>();
        for (List<Text.Line> row : rows) {
            for (Text.Line line : row) {
                if (line.getText().toLowerCase().contains(targetUserName.toLowerCase())) {
                    nameCandidates.add(line);
                    rowCandidates.add(row);
                }
            }
        }

        if (!nameCandidates.isEmpty()) {
            if (nameCandidates.size() == 1 || targetRole == null || targetRole.isEmpty()) {
                targetUserElement = nameCandidates.get(0);
                namesRow = rowCandidates.get(0);
                Log.d(TAG, "Wybrano pierwsze/jedyne dopasowanie imienia: " + targetUserElement.getText());
            } else {
                Log.d(TAG, "Znaleziono wiele osób o imieniu " + targetUserName + ". Szukam roli: " + targetRole);
                for (int i = 0; i < nameCandidates.size(); i++) {
                    Text.Line candidate = nameCandidates.get(i);
                    List<Text.Line> cRow = rowCandidates.get(i);
                    int rowIndex = rows.indexOf(cRow);
                    
                    // Szukamy roli w wierszu wyżej
                    if (rowIndex > 0) {
                        List<Text.Line> roleRow = rows.get(rowIndex - 1);
                        Text.Line bestRole = null;
                        int minDiff = Integer.MAX_VALUE;
                        for (Text.Line roleCell : roleRow) {
                            if (roleCell.getBoundingBox() == null || candidate.getBoundingBox() == null) continue;
                            int diff = Math.abs(roleCell.getBoundingBox().centerX() - candidate.getBoundingBox().centerX());
                            if (diff < minDiff && diff < 200) {
                                minDiff = diff;
                                bestRole = roleCell;
                            }
                        }
                        if (bestRole != null && bestRole.getText().toLowerCase().contains(targetRole.toLowerCase())) {
                            targetUserElement = candidate;
                            namesRow = cRow;
                            Log.d(TAG, "Dopasowano osobę na podstawie roli: " + bestRole.getText());
                            break;
                        }
                    }
                }
                
                // Fallback jeśli żadna rola nie pasowała idealnie
                if (targetUserElement == null) {
                    targetUserElement = nameCandidates.get(0);
                    namesRow = rowCandidates.get(0);
                    Log.d(TAG, "Żadna rola nie pasowała idealnie, wybrano pierwszą osobę z brzegu.");
                }
            }
        }

        // KROK 2: Jeśli nie ma targetUserName (ktoś nie podał imienia w apce), szukamy wiersza z imionami na podstawie ról.
        if (namesRow == null || targetUserElement == null) {
            Log.w(TAG, "Nie znaleziono imienia " + targetUserName + " w żadnym wierszu. Szukam wiersza z rolami.");
            for (int i = 0; i < rows.size(); i++) {
                List<Text.Line> row = rows.get(i);
                boolean hasRole = false;
                for (Text.Line line : row) {
                    String text = line.getText().trim().toLowerCase();
                    if (text.contains("team leader") || text.contains("leader") || text.contains("manager") || text.contains("deputy") || text.equals("tl") || text.equals("dm") || text.contains("kasa") || text.contains("obsługa")) {
                        hasRole = true;
                        break;
                    }
                }
                if (hasRole) {
                    // Znaleźliśmy wiersz z rolami. Imiona mogą być w tym samym bloku (wieloliniowe) albo w następnym wierszu.
                    // Sprawdźmy, czy ten wiersz ma jakieś imiona (nie tylko role)
                    namesRow = row;
                    if (i + 1 < rows.size()) {
                        List<Text.Line> nextRow = rows.get(i + 1);
                        boolean hasDayName = false;
                        for (Text.Line line : row) {
                            String lText = line.getText().toLowerCase();
                            if (lText.matches(".*(pon|wt|śr|czw|pt|sob|ndz|monday|tue|wed|thu|fri|sat|sun).*")) {
                                hasDayName = true;
                                break;
                            }
                        }
                        boolean isDaysRow = false;
                        for (Text.Line nl : nextRow) {
                            if (nl.getText().toLowerCase().matches(".*(pon|wt|śr|czw|pt|sob|ndz|monday|tuesday).*")) {
                                isDaysRow = true;
                                break;
                            }
                        }
                        if (!isDaysRow && nextRow.size() > 2) {
                            namesRow = nextRow;
                        }
                    }
                    break;
                }
            }
        }

        if (namesRow != null && targetUserElement == null) {
            warnings.add(new ParserWarning(ParserWarning.Type.UNKNOWN_NAME, "Nie znaleziono imienia " + targetUserName + ". Wybrano pierwszą osobę."));
            for (Text.Line line : namesRow) {
                String text = line.getText().trim().toLowerCase();
                if (!text.isEmpty() && !text.contains("godz") && !text.matches(".*(pon|wt|śr|czw|pt|sob|ndz|monday|tuesday|wednesday|thursday|friday|saturday|sunday|august|lipiec).*")) {
                    targetUserElement = line;
                    break;
                }
            }
            if (targetUserElement == null && !namesRow.isEmpty()) {
                targetUserElement = namesRow.get(0);
            }
        }
        
        if (targetUserElement == null) {
            throw new IllegalArgumentException("Nie wykryto struktury tabeli ani żadnego imienia w zdjęciu grafiku.");
        }

        int namesRowIndex = rows.indexOf(namesRow);
        List<Text.Line> roleRow = (namesRowIndex > 0) ? rows.get(namesRowIndex - 1) : new ArrayList<>();

        Map<Text.Line, String> uniqueNamesMap = new HashMap<>();
        Map<String, Integer> nameCounts = new HashMap<>();
        
        for (Text.Line nameElement : namesRow) {
            String nameStr = nameElement.getText().trim();
            if (!nameStr.isEmpty() && !nameStr.toLowerCase().contains("godz") && nameStr.length() > 2) {
                
                // Szukamy roli dla tego imienia w wierszu wyżej
                String roleSuffix = "";
                int minDiff = Integer.MAX_VALUE;
                for (Text.Line roleCell : roleRow) {
                    if (roleCell.getBoundingBox() == null || nameElement.getBoundingBox() == null) continue;
                    int diff = Math.abs(roleCell.getBoundingBox().centerX() - nameElement.getBoundingBox().centerX());
                    if (diff < minDiff && diff < 200) { // Tolerancja dopasowania w kolumnie
                        minDiff = diff;
                        String roleText = roleCell.getText().toLowerCase();
                        if (roleText.contains("team leader") || roleText.contains("tl")) {
                            roleSuffix = " (TL)";
                        } else if (roleText.contains("manager") || roleText.contains("menadżer") || roleText.contains("kierownik")) {
                            roleSuffix = " (Manager)";
                        } else if (roleText.contains("z-ca") || roleText.contains("zastępca") || roleText.contains("deputy")) {
                            roleSuffix = " (Deputy)";
                        }
                    }
                }
                
                // Doklej rolę
                nameStr = nameStr + roleSuffix;

                // Sprawdź duplikaty po doklejeniu roli (np. jeśli byłoby dwóch "Kacper (TL)")
                int count = nameCounts.getOrDefault(nameStr, 0) + 1;
                nameCounts.put(nameStr, count);
                if (count > 1) {
                    nameStr = nameStr + " (" + count + ")"; // Np. Kacper (TL) (2)
                }
                uniqueNamesMap.put(nameElement, nameStr);
                foundNames.add(nameStr);
                scheduleByName.put(nameStr, new ArrayList<>());
            }
        }

        int lastDayNum = 0;
        for (List<Text.Line> row : rows) {
            if (row.isEmpty()) continue;
            
            String firstCell = row.get(0).getText().trim();
            String secondCell = row.size() > 1 ? row.get(1).getText().trim() : "";
            
            int dayNum = -1;
            int parsedNumber = -1;
            try {
                if (firstCell.matches(".*\\d.*")) {
                    String[] parts = firstCell.split("[^0-9]+");
                    for (String part : parts) {
                        if (!part.isEmpty()) {
                            int candidate = Integer.parseInt(part);
                            if (candidate >= 1 && candidate <= 31) {
                                parsedNumber = candidate;
                                break;
                            }
                        }
                    }
                    if (parsedNumber < 0) {
                        parsedNumber = Integer.parseInt(firstCell.replaceAll("[^0-9]", ""));
                    }
                } else if (secondCell.matches(".*\\d.*")) {
                    String[] parts = secondCell.split("[^0-9]+");
                    for (String part : parts) {
                        if (!part.isEmpty()) {
                            int candidate = Integer.parseInt(part);
                            if (candidate >= 1 && candidate <= 31) {
                                parsedNumber = candidate;
                                break;
                            }
                        }
                    }
                    if (parsedNumber < 0) {
                        parsedNumber = Integer.parseInt(secondCell.replaceAll("[^0-9]", ""));
                    }
                }
            } catch (NumberFormatException e) {}
            
            boolean hasDayName = firstCell.toLowerCase().matches(".*(pon|wt|śr|czw|pt|sob|ndz|monday|tuesday|wednesday|thursday|friday|saturday|sunday).*") ||
                                 secondCell.toLowerCase().matches(".*(pon|wt|śr|czw|pt|sob|ndz|monday|tuesday|wednesday|thursday|friday|saturday|sunday).*");

            if (parsedNumber == lastDayNum + 1) {
                dayNum = parsedNumber;
            } else if (hasDayName) {
                // Skoro to jest nazwa dnia, to po prostu wchodzimy w kolejny wiersz siatki kalendarza!
                // Olej błędy OCR (np. OCR wyczytał '25' zamiast '23' w '23 Sunday').
                dayNum = lastDayNum + 1;
            } else if (parsedNumber > lastDayNum && parsedNumber <= 31) {
                dayNum = parsedNumber;
            }
            
            if (dayNum < 1 || dayNum > 31) continue;
            // Zabezpieczenie przed losowymi numerami z sumowania
            // Dzień musi rosnąć (nie może być mniejszy niż poprzedni o ile to nie z nazwy dnia, chociaż nazwa podbija lastDayNum + 1)
            // I nie może urosnąć o więcej niż 5.
            if ((dayNum - lastDayNum > 5 || dayNum < lastDayNum) && !hasDayName && lastDayNum != 0) continue;
            lastDayNum = dayNum;

            String dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, monthValue, dayNum);
            if (!allDates.contains(dateStr)) allDates.add(dateStr);

            List<String> coworkersForDay = new ArrayList<>();

            List<MatchPair> matches = new ArrayList<>();
            for (Text.Line cellEl : row) {
                String cText = cellEl.getText().trim();
                if (cText.equals(firstCell) || cText.equals(secondCell) || cText.toLowerCase().matches(".*(pon|wt|śr|czw|pt|sob|ndz|monday|tue|wed|thu|fri|sat|sun).*")) {
                    continue;
                }
                for (Text.Line nameEl : namesRow) {
                    if (cellEl.getBoundingBox() != null && nameEl.getBoundingBox() != null) {
                        int diff = Math.abs(cellEl.getBoundingBox().centerX() - nameEl.getBoundingBox().centerX());
                        if (diff < 150) {
                            matches.add(new MatchPair(nameEl, cellEl, diff));
                        }
                    }
                }
            }
            
            Collections.sort(matches, (a, b) -> Integer.compare(a.diff, b.diff));
            Map<Text.Line, List<Text.Line>> personToCells = new HashMap<>();
            Set<Text.Line> usedCells = new HashSet<>();
            
            for (MatchPair match : matches) {
                if (!usedCells.contains(match.cell)) {
                    usedCells.add(match.cell);
                    personToCells.computeIfAbsent(match.person, k -> new ArrayList<>()).add(match.cell);
                }
            }

            for (Text.Line nameEl : namesRow) {
                List<Text.Line> assigned = personToCells.get(nameEl);
                if (assigned == null || assigned.isEmpty()) continue;

                Collections.sort(assigned, (a, b) -> {
                    if (a.getBoundingBox() == null || b.getBoundingBox() == null) return 0;
                    return Integer.compare(a.getBoundingBox().centerX(), b.getBoundingBox().centerX());
                });

                StringBuilder fullText = new StringBuilder();
                int sumDiff = 0;
                for (Text.Line c : assigned) {
                    fullText.append(c.getText().trim()).append(" ");
                    sumDiff += Math.abs(c.getBoundingBox().centerX() - nameEl.getBoundingBox().centerX());
                }
                
                String rawText = fullText.toString().trim();
                NewFormatExcelParser.ParsedShiftInfo info = shiftParser.parseShiftText(rawText);
                String empName = uniqueNamesMap.containsKey(nameEl) ? uniqueNamesMap.get(nameEl) : nameEl.getText().trim();
                boolean isTargetUser = (nameEl == targetUserElement);

                // Diagnostyka: loguj rawText dla użytkownika oraz dla każdego kto dostał WOLNE
                if (isTargetUser && !info.isShift) {
                    Log.d(TAG, "[" + dateStr + "] KACPER WOLNE — rawText='" + rawText + "'");
                } else if (isTargetUser) {
                    Log.d(TAG, "[" + dateStr + "] Kacper zmiana: '" + rawText + "' → " + info.startTime + "-" + info.endTime);
                }

                if (info.isShift) {
                    GlobalShift gs = new GlobalShift(empName, dateStr, info.startTime, info.endTime, info.category);
                    gs.setManuallyEdited(false);
                    allGlobalShifts.add(gs);

                    if (isTargetUser) {
                        Shift userShift = new Shift(
                                dateStr, info.startTime, info.endTime, info.description != null ? info.description : "",
                                true, false, info.category
                        );
                        userShift.setClosingShift(info.isClosingShift);
                        
                        boolean alreadyHasShift = false;
                        for (Shift s : targetUserShifts) {
                            if (dateStr.equals(s.getDate())) {
                                alreadyHasShift = true;
                                break;
                            }
                        }
                        if (!alreadyHasShift) {
                            targetUserShifts.add(userShift);
                            String n = uniqueNamesMap.containsKey(targetUserElement) ? uniqueNamesMap.get(targetUserElement) : targetUserElement.getText().trim();
                            if (scheduleByName.containsKey(n)) scheduleByName.get(n).add(userShift);
                        }
                    } else {
                        if (!coworkersForDay.contains(empName)) coworkersForDay.add(empName);
                    }
                } else if (isTargetUser) {
                     Shift userShift = new Shift(dateStr, "", "", "WOLNE", false, false, "WOLNE");
                     targetUserShifts.add(userShift);
                     String n = uniqueNamesMap.containsKey(targetUserElement) ? uniqueNamesMap.get(targetUserElement) : targetUserElement.getText().trim();
                     if (scheduleByName.containsKey(n)) scheduleByName.get(n).add(userShift);
                }
            }

            // Jeśli kolumna Kacpra była pusta (OCR nie wychwycił żadnej komórki) →
            // dodaj WOLNE żeby dzień nie zniknął całkowicie z listy
            boolean targetUserHasEntryForToday = false;
            for (Shift s : targetUserShifts) {
                if (dateStr.equals(s.getDate())) {
                    targetUserHasEntryForToday = true;
                    break;
                }
            }
            if (!targetUserHasEntryForToday) {
                Log.d(TAG, "[" + dateStr + "] KACPER WOLNE — rawText='(brak komórki OCR)'");
                Shift userShift = new Shift(dateStr, "", "", "WOLNE", false, false, "WOLNE");
                targetUserShifts.add(userShift);
                String n = uniqueNamesMap.containsKey(targetUserElement) ? uniqueNamesMap.get(targetUserElement) : targetUserElement.getText().trim();
                if (scheduleByName.containsKey(n)) scheduleByName.get(n).add(userShift);
            }
            // Po iteracji po wszystkich osobach, uzupełniamy closingCrew dla shift Kacpra
            if (!coworkersForDay.isEmpty()) {
                Log.d(TAG, "[" + dateStr + "] Współpracownicy na zmianie: " + String.join(", ", coworkersForDay));
                for (int si = targetUserShifts.size() - 1; si >= 0; si--) {
                    if (targetUserShifts.get(si).getDate() != null &&
                            targetUserShifts.get(si).getDate().equals(dateStr)) {
                        targetUserShifts.get(si).setClosingCrew(String.join(", ", coworkersForDay));
                        break;
                    }
                }
            }
        }

        ScheduleParseResult parseResult = new ScheduleParseResult(
                scheduleByName,
                allDates,
                foundNames,
                allGlobalShifts,
                warnings,
                0.75f,
                "OCR ML Kit"
        );
        parseResult.setTargetUserShifts(targetUserShifts);

        int shiftCount = targetUserShifts.size();
        Log.d(TAG, "============ PODSUMOWANIE PARSOWANIA OCR ============");
        Log.d(TAG, "Wykryte osoby: " + String.join(", ", foundNames));
        Log.d(TAG, "Zmiany Kacpra: " + shiftCount);
        for (int i = 0; i < targetUserShifts.size(); i++) {
            Shift s = targetUserShifts.get(i);
            String crew = s.getClosingCrew() != null && !s.getClosingCrew().isEmpty()
                    ? " | Ekipa: " + s.getClosingCrew() : "";
            Log.d(TAG, "  " + (i + 1) + ". " + s.getDate() + " " + s.getStartTime() +
                    "-" + s.getEndTime() + " (" + s.getCategory() + ")" + crew);
        }
        Log.d(TAG, "=====================================================");
        return parseResult;
    }

    private static class MatchPair {
        Text.Line person;
        Text.Line cell;
        int diff;

        MatchPair(Text.Line person, Text.Line cell, int diff) {
            this.person = person;
            this.cell = cell;
            this.diff = diff;
        }
    }
}
