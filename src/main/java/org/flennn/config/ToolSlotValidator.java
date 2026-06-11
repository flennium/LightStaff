package org.flennn.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ToolSlotValidator {
    private ToolSlotValidator() {
    }

    public static List<String> validate(Map<String, Integer> slots) {
        List<String> warnings = new ArrayList<>();
        Map<Integer, String> seenSlots = new HashMap<>();

        for (Map.Entry<String, Integer> entry : slots.entrySet()) {
            String tool = entry.getKey();
            int slot = entry.getValue();
            if (slot < 0 || slot > 8) {
                warnings.add("Tool '" + tool + "' has invalid hotbar slot " + slot + ". Valid range is 0-8.");
                continue;
            }

            String existing = seenSlots.putIfAbsent(slot, tool);
            if (existing != null) {
                warnings.add("Tool '" + tool + "' shares hotbar slot " + slot + " with '" + existing + "'.");
            }
        }

        return warnings;
    }
}
