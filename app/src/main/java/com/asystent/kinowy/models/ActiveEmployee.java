package com.asystent.kinowy.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Encja — słownik pracowników kina (kadra, role, uprawnienia i status zatrudnienia).
 * <p>
 * Klucz główny = {@code name} — unikalne imię/nazwisko (np. „Kacper W.”, „Zuzanna R.”).
 * <p>
 * Od wersji Room v16:
 * - {@code isActive}: flaga określająca, czy pracownik nadal pracuje w kinie.
 * - {@code role}: rola pracownika (EMPLOYEE / TL / MANAGER) wykorzystywana przez {@code ReplacementFinder}.
 * - {@code lastSeenDate}: data ostatniej odnotowanej zmiany (yyyy-MM-dd).
 */
@Entity(tableName = "active_employees")
public class ActiveEmployee {

    public static final String ROLE_EMPLOYEE = "EMPLOYEE";
    public static final String ROLE_TL       = "TL";
    public static final String ROLE_MANAGER  = "MANAGER";

    @PrimaryKey
    @NonNull
    private String name;

    @ColumnInfo(name = "is_active", defaultValue = "1")
    private boolean isActive = true;

    @ColumnInfo(name = "role", defaultValue = "'EMPLOYEE'")
    private String role = ROLE_EMPLOYEE;

    @ColumnInfo(name = "last_seen_date")
    private String lastSeenDate;

    public ActiveEmployee(@NonNull String name) {
        this.name = name;
        this.isActive = true;
        this.role = ROLE_EMPLOYEE;
    }

    @androidx.room.Ignore
    public ActiveEmployee(@NonNull String name, boolean isActive, String role, String lastSeenDate) {
        this.name = name;
        this.isActive = isActive;
        this.role = role != null ? role : ROLE_EMPLOYEE;
        this.lastSeenDate = lastSeenDate;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getRole() {
        return role != null ? role : ROLE_EMPLOYEE;
    }

    public void setRole(String role) {
        this.role = role != null ? role : ROLE_EMPLOYEE;
    }

    public String getLastSeenDate() {
        return lastSeenDate;
    }

    public void setLastSeenDate(String lastSeenDate) {
        this.lastSeenDate = lastSeenDate;
    }

    public boolean isLeadership() {
        return ROLE_TL.equalsIgnoreCase(role) || ROLE_MANAGER.equalsIgnoreCase(role);
    }
}
