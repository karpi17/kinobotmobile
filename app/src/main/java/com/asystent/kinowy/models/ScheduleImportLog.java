package com.asystent.kinowy.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Encja reprezentująca pojedynczy import grafiku (Excel, PDF, OCR).
 * Służy do śledzenia historii importów i ew. wycofywania zmian.
 */
@Entity(tableName = "import_log")
public class ScheduleImportLog {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "source_type")
    private String sourceType;

    @ColumnInfo(name = "source_file")
    private String sourceFile;

    @ColumnInfo(name = "imported_at")
    private String importedAt;

    @ColumnInfo(name = "shifts_added")
    private int shiftsAdded;

    @ColumnInfo(name = "shifts_skipped")
    private int shiftsSkipped;

    @ColumnInfo(name = "confidence")
    private float confidence;

    public ScheduleImportLog(String sourceType, String sourceFile, String importedAt,
                            int shiftsAdded, int shiftsSkipped, float confidence) {
        this.sourceType = sourceType;
        this.sourceFile = sourceFile;
        this.importedAt = importedAt;
        this.shiftsAdded = shiftsAdded;
        this.shiftsSkipped = shiftsSkipped;
        this.confidence = confidence;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public String getImportedAt() {
        return importedAt;
    }

    public int getShiftsAdded() {
        return shiftsAdded;
    }

    public int getShiftsSkipped() {
        return shiftsSkipped;
    }

    public float getConfidence() {
        return confidence;
    }
}
