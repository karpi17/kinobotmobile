package com.asystent.kinowy.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Encja — słownik aktywnych pracowników kina.
 * <p>
 * Zasilana automatycznie podczas parsowania plików .xlsx z grafikami.
 * Służy jako źródło podpowiedzi (AutoComplete) w polu „Ekipa zamykająca".
 * <p>
 * Klucz główny = {@code name} — zapobiega duplikatom.
 * Format: „Imię N." (np. „Kacper W.", „Zuzanna R.").
 */
@Entity(tableName = "active_employees")
public class ActiveEmployee {

    @PrimaryKey
    @NonNull
    private String name;

    public ActiveEmployee(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }
}
