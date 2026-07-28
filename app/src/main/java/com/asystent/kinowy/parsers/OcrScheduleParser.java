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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
                    if (text.contains("team leader") || text.contains("manager") || text.contains("kasa") || text.contains("obsługa")) {
                        hasRole = true;
                        break;
                    }
                }
                if (hasRole) {
                    // Znaleźliśmy wiersz z rolami. Imiona mogą być w tym samym bloku (wieloliniowe) albo w następnym wierszu.
                    // Sprawdźmy, czy ten wiersz ma jakieś imiona (nie tylko role)
                    namesRow = row;
                    // Jeśli to czyste role jak "[manager] [team leader]", weźmy wiersz poniżej o ile istnieje.
                    if (i + 1 < rows.size()) {
                        List<Text.Line> nextRow = rows.get(i + 1);
                        // Sprawdzamy czy nextRow nie jest przypadkiem dniami tygodnia
                        boolean isDaysRow = false;
                        for (Text.Line nl : nextRow) {
                            if (nl.getText().toLowerCase().matches(".*(pon|wt|śr|czw|pt|sob|ndz|monday|tuesday).*")) {
                                isDaysRow = true;
                                break;
                            }
                        }
                        if (!isDaysRow && nextRow.size() > 2) {
                            Log.d(TAG, "Wiersz ról to index " + i + ", bierzemy wiersz imion z indexu " + (i + 1));
                            namesRow = nextRow;
                        }
                    }
                    break;
                }
            }
        }

        // KROK 3: Fallback z namesRow
        if (namesRow != null && targetUserElement == null) {
            warnings.add(new ParserWarning(ParserWarning.Type.UNKNOWN_NAME, "Nie znaleziono imienia " + targetUserName + ". Wybrano pierwszą osobę."));
            for (Text.Line line : namesRow) {
                String text = line.getText().trim().toLowerCase();
                if (!text.isEmpty() && !text.contains("godz") && !text.matches(".*(pon|wt|śr|czw|pt|sob|ndz|monday|tuesday|august|lipiec).*")) {
                    targetUserElement = line;
                    Log.d(TAG, "Fallback: wybrano -> " + text);
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

        for (Text.Line nameElement : namesRow) {
            String nameStr = nameElement.getText().trim();
            if (!nameStr.isEmpty() && !nameStr.toLowerCase().contains("godz") && nameStr.length() > 2) {
                foundNames.add(nameStr);
                scheduleByName.put(nameStr, new ArrayList<>());
            }
        }

        // Iteracja po rzędach dni (szukamy rzędów zaczynających się od numeru dnia 1..31)
        int lastDayNum = 0;
        for (List<Text.Line> row : rows) {
            if (row.isEmpty()) continue;
            
            String firstCell = row.get(0).getText().trim();
            String secondCell = row.size() > 1 ? row.get(1).getText().trim() : "";
            
            int dayNum = -1;
            int parsedNumber = -1;
            try {
                if (firstCell.matches(".*\\d.*")) {
                    parsedNumber = Integer.parseInt(firstCell.replaceAll("[^0-9]", ""));
                } else if (secondCell.matches(".*\\d.*")) {
                    parsedNumber = Integer.parseInt(secondCell.replaceAll("[^0-9]", ""));
                }
            } catch (Exception ignored) {}

            boolean hasDayName = firstCell.toLowerCase().matches(".*(pon|wt|śr|czw|pt|sob|ndz|monday|tuesday|wednesday|thursday|friday|saturday|sunday).*") ||
                                 secondCell.toLowerCase().matches(".*(pon|wt|śr|czw|pt|sob|ndz|monday|tuesday|wednesday|thursday|friday|saturday|sunday).*");

            if (parsedNumber > lastDayNum && parsedNumber <= 31) {
                dayNum = parsedNumber;
            } else if (hasDayName) {
                // OCR zjadł numer dnia, ale jest nazwa dnia tygodnia
                dayNum = lastDayNum + 1;
            }
            
            if (dayNum < 1 || dayNum > 31) continue;
            
            // Zabezpieczenie przed losowymi numerami (np. suma godzin na dole)
            if (dayNum - lastDayNum > 5 && !hasDayName && lastDayNum != 0) {
                Log.d(TAG, "Ignoruję fałszywy dzień: " + dayNum + " (za duży przeskok i brak nazwy dnia)");
                continue;
            }

            lastDayNum = dayNum;

            String dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, monthValue, dayNum);
            if (!allDates.contains(dateStr)) {
                allDates.add(dateStr);
            }

            List<String> coworkersForDay = new ArrayList<>();

            // Przypisywanie zmian do pracowników na podstawie współrzędnych X
            for (Text.Line nameEl : namesRow) {
                if (nameEl.getBoundingBox() == null) continue;
                int nameCenterX = nameEl.getBoundingBox().centerX();
                
                // Znajdź blok tekstu z godzinami w tym samym rzędzie, który jest najbliżej centerX imienia
                Text.Line bestMatch = null;
                int minDiff = Integer.MAX_VALUE;
                for (Text.Line cellEl : row) {
                    if (cellEl.getBoundingBox() == null) continue;
                    int cellCenterX = cellEl.getBoundingBox().centerX();
                    int diff = Math.abs(cellCenterX - nameCenterX);
                    if (diff < minDiff && diff < 150) { // Tolerancja 150 pikseli (zależy od rozdzielczości)
                        minDiff = diff;
                        bestMatch = cellEl;
                    }
                }

                if (bestMatch != null) {
                    String rawText = bestMatch.getText();
                    NewFormatExcelParser.ParsedShiftInfo info = shiftParser.parseShiftText(rawText);

                    if (info.isShift) {
                        String empName = nameEl.getText().trim();
                        GlobalShift gs = new GlobalShift(empName, dateStr, info.startTime, info.endTime, info.category);
                        gs.setManuallyEdited(false);
                        allGlobalShifts.add(gs);
                        
                        if (!empName.equalsIgnoreCase(targetUserElement.getText().trim())) {
                            if (!coworkersForDay.contains(empName)) coworkersForDay.add(empName);
                        }
                    }
                }
            }

            // Target user shift
            if (targetUserElement != null && targetUserElement.getBoundingBox() != null) {
                int targetCenterX = targetUserElement.getBoundingBox().centerX();
                Text.Line bestMatch = null;
                int minDiff = Integer.MAX_VALUE;
                for (Text.Line cellEl : row) {
                    if (cellEl.getBoundingBox() == null) continue;
                    int cellCenterX = cellEl.getBoundingBox().centerX();
                    int diff = Math.abs(cellCenterX - targetCenterX);
                    if (diff < minDiff && diff < 150) {
                        minDiff = diff;
                        bestMatch = cellEl;
                    }
                }

                if (bestMatch != null) {
                    Log.d(TAG, "Dla dnia " + dateStr + " dopasowano komórkę: [" + bestMatch.getText() + "] (diff: " + minDiff + ")");
                    NewFormatExcelParser.ParsedShiftInfo info = shiftParser.parseShiftText(bestMatch.getText());
                    Log.d(TAG, "-> Parsowanie komórki [" + bestMatch.getText() + "] zwróciło isShift=" + info.isShift);
                    if (info.isShift) {
                        Shift userShift = new Shift(
                                dateStr, info.startTime, info.endTime, info.description != null ? info.description : "",
                                true, false, info.category
                        );
                        userShift.setClosingShift(info.isClosingShift);
                        if (!coworkersForDay.isEmpty()) {
                            userShift.setClosingCrew(String.join(", ", coworkersForDay));
                        }
                        targetUserShifts.add(userShift);
                        Log.d(TAG, "-> Zapisano poprawną zmianę: " + info.startTime + " - " + info.endTime);

                        
                        String n = targetUserElement.getText().trim();
                        if (scheduleByName.containsKey(n)) {
                            scheduleByName.get(n).add(userShift);
                        }
                    } else {
                        Log.d(TAG, "-> Zmiana odrzucona (to nie jest zmiana). Traktowane jako WOLNE.");
                        Shift userShift = new Shift(
                                dateStr, "", "", "WOLNE", false, false, "UNKNOWN"
                        );
                        targetUserShifts.add(userShift);
                        String n = targetUserElement.getText().trim();
                        if (scheduleByName.containsKey(n)) {
                            scheduleByName.get(n).add(userShift);
                        }
                    }
                } else {
                    Log.d(TAG, "Dla dnia " + dateStr + " nie znaleziono żadnej komórki spełniającej warunek diff < 150.");
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

        Log.d(TAG, "Parsowanie OCR zakończone. Zmiany: " + targetUserShifts.size());
        return parseResult;
    }
}
