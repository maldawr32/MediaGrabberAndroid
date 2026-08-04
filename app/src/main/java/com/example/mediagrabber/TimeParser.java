package com.example.mediagrabber;

import java.util.Locale;

final class TimeParser {
    private TimeParser() {
    }

    static long parseOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return -1;
        }
        String[] parts = value.trim().split(":");
        if (parts.length < 1 || parts.length > 3) {
            throw new IllegalArgumentException("استخدم صيغة الوقت HH:MM:SS أو MM:SS");
        }
        long total = 0;
        try {
            for (int index = 0; index < parts.length; index++) {
                String part = parts[index];
                if (part.isEmpty()) {
                    throw new NumberFormatException();
                }
                long component = Long.parseLong(part);
                if (component < 0 || (index > 0 && component > 59)) {
                    throw new NumberFormatException();
                }
                total = Math.addExact(Math.multiplyExact(total, 60), component);
            }
        } catch (NumberFormatException | ArithmeticException error) {
            throw new IllegalArgumentException("وقت غير صالح: " + value);
        }
        return total;
    }

    static String normalize(String value) {
        long total = parseOptional(value);
        if (total < 0) {
            return "";
        }
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
