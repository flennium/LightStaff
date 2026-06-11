package org.flennn.util;

import org.bukkit.Material;

public final class MaterialResolver {
    private MaterialResolver() {
    }

    public static Material firstAvailable(String configured, Material fallback) {
        if (configured == null || configured.isBlank()) {
            return fallback;
        }

        for (String candidate : configured.split(",")) {
            Material material = Material.matchMaterial(candidate.trim());
            if (material != null && material.isItem()) {
                return material;
            }
        }

        return fallback;
    }
}
