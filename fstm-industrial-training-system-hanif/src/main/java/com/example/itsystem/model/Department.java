package com.example.itsystem.model;

public enum Department {
    FOOD_SCIENCE("FOOD SCIENCE"),
    FOOD_TECHNOLOGY("FOOD TECHNOLOGY"),
    FOOD_SERVICE_AND_MANAGEMENT("FOOD SERVICE AND MANAGEMENT");

    private final String label;

    Department(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
