package me.fulcanelly.tgbridge.boundaryguard.services.restrictions;

import java.util.List;

import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class RestrictionService {

    private final Condition accessCondition;
    private final List<Restriction> restrictions;

    public RestrictionService(Condition accessCondition, List<Restriction> restrictions) {
        this.accessCondition = accessCondition;
        this.restrictions = List.copyOf(restrictions);
    }

    public boolean refresh(Player player) {
        boolean restricted = !accessCondition.isMet(player);
        restrictions.forEach(restriction -> restriction.refresh(player, restricted));
        return restricted;
    }

    public boolean allowsMovement(Player player, Location destination) {
        if (!refresh(player)) {
            return true;
        }
        return restrictions.stream().allMatch(restriction -> restriction.allowsMovement(player, destination));
    }

    public boolean allowsContainerUse(Player player) {
        if (!refresh(player)) {
            return true;
        }
        return restrictions.stream().allMatch(restriction -> restriction.allowsContainerUse(player));
    }

    public boolean blocksContainerUse(Player player, InventoryType inventoryType) {
        return isContainer(inventoryType) && !allowsContainerUse(player);
    }

    public void forget(Player player) {
        restrictions.forEach(restriction -> restriction.forget(player));
    }

    private boolean isContainer(InventoryType type) {
        switch (type) {
            case CHEST:
            case DISPENSER:
            case DROPPER:
            case FURNACE:
            case BREWING:
            case ENDER_CHEST:
            case HOPPER:
            case SHULKER_BOX:
            case BARREL:
            case BLAST_FURNACE:
            case SMOKER:
                return true;
            default:
                return false;
        }
    }
}
