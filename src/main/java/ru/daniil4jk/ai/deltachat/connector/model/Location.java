package ru.daniil4jk.ai.deltachat.connector.model;

import lombok.Data;

/**
 * Геопозиция (широта/долгота).
 */
@Data
public class Location {
    private double latitude;
    private double longitude;
}
