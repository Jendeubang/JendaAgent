package com.jd.genie.service.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts OCR engine geometry rows into user-facing text while preserving lines. */
final class OcrTextNormalizer {
    private static final Pattern GEOMETRY_PREFIX = Pattern.compile(
            "^\\s*(?:[-+]?\\d+(?:\\.\\d+)?\\s*,\\s*){5}(.+?)\\s*$");

    private OcrTextNormalizer() {
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] lines = raw.replace("\r\n", "\n").split("\n", -1);
        StringBuilder normalized = new StringBuilder(raw.length());
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                normalized.append('\n');
            }
            Matcher matcher = GEOMETRY_PREFIX.matcher(lines[index]);
            normalized.append(matcher.matches() ? matcher.group(1) : lines[index]);
        }
        return normalized.toString().strip();
    }
}