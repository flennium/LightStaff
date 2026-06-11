package org.flennn.tools;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class InspectInventoryHolder implements InventoryHolder {
    private final UUID targetUuid;

    public InspectInventoryHolder(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    @Override
    public Inventory getInventory() {
        throw new UnsupportedOperationException("InspectInventoryHolder is only a marker holder");
    }
}
