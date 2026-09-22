package com.asystent.kinowy.models;

import java.util.Objects;

public class BhpAlert {
    private final String id;
    private final String message;
    private final String type; // np. "CLOPEN", "MARATHON", "OVERTIME"
    private final String date;

    public BhpAlert(String id, String message, String type, String date) {
        this.id = id;
        this.message = message;
        this.type = type;
        this.date = date;
    }

    public String getId() { return id; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public String getDate() { return date; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BhpAlert bhpAlert = (BhpAlert) o;
        return Objects.equals(id, bhpAlert.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
