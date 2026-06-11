package org.flennn.util;

import java.util.Map;

public final class CommandTemplate {
    private CommandTemplate() {
    }

    public static String render(String template, Map<String, String> placeholders) {
        if (template == null || template.isBlank()) return "";

        String rendered = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", sanitizeValue(entry.getValue()));
        }
        return rendered.trim();
    }

    private static String sanitizeValue(String value) {
        if (value == null) return "";
        return value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .replace(';', ',')
                .trim();
    }
}
