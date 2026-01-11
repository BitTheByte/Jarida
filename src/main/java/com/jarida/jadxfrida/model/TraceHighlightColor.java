package com.jarida.jadxfrida.model;

import java.awt.Color;

public enum TraceHighlightColor {
    GREEN("Green (default)", new Color(78, 145, 90, 70), "78,145,90,70"),
    BLUE("Blue", new Color(80, 130, 220, 70), "80,130,220,70"),
    ORANGE("Orange", new Color(210, 150, 60, 70), "210,150,60,70"),
    RED("Red", new Color(200, 95, 95, 70), "200,95,95,70"),
    PURPLE("Purple", new Color(150, 90, 190, 70), "150,90,190,70"),
    GRAY("Gray", new Color(120, 120, 120, 70), "120,120,120,70");

    private final String label;
    private final Color color;
    private final String legacyValue;

    TraceHighlightColor(String label, Color color, String legacyValue) {
        this.label = label;
        this.color = color;
        this.legacyValue = legacyValue;
    }

    public Color getColor() {
        return color;
    }

    @Override
    public String toString() {
        return label;
    }

    public static TraceHighlightColor fromString(String value) {
        if (value == null) {
            return GREEN;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return GREEN;
        }
        for (TraceHighlightColor preset : values()) {
            if (trimmed.equalsIgnoreCase(preset.name())
                    || trimmed.equalsIgnoreCase(preset.label)
                    || (preset.legacyValue != null && trimmed.equalsIgnoreCase(preset.legacyValue))) {
                return preset;
            }
        }
        Color parsed = parseColor(trimmed);
        if (parsed != null) {
            for (TraceHighlightColor preset : values()) {
                if (preset.color.getRGB() == parsed.getRGB()) {
                    return preset;
                }
            }
        }
        return GREEN;
    }

    private static Color parseColor(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String normalized = trimmed;
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.matches("(?i)^[0-9a-f]{6}$")) {
            int r = Integer.parseInt(normalized.substring(0, 2), 16);
            int g = Integer.parseInt(normalized.substring(2, 4), 16);
            int b = Integer.parseInt(normalized.substring(4, 6), 16);
            return new Color(r, g, b, GREEN.color.getAlpha());
        }
        if (normalized.matches("(?i)^[0-9a-f]{8}$")) {
            int r = Integer.parseInt(normalized.substring(0, 2), 16);
            int g = Integer.parseInt(normalized.substring(2, 4), 16);
            int b = Integer.parseInt(normalized.substring(4, 6), 16);
            int a = Integer.parseInt(normalized.substring(6, 8), 16);
            return new Color(r, g, b, a);
        }
        String[] parts = trimmed.split(",");
        if (parts.length == 3 || parts.length == 4) {
            int r = parseChannel(parts[0]);
            int g = parseChannel(parts[1]);
            int b = parseChannel(parts[2]);
            int a = parts.length == 4 ? parseChannel(parts[3]) : GREEN.color.getAlpha();
            if (r < 0 || g < 0 || b < 0 || a < 0) {
                return null;
            }
            return new Color(r, g, b, a);
        }
        return null;
    }

    private static int parseChannel(String value) {
        if (value == null) {
            return -1;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed < 0 || parsed > 255) {
                return -1;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
