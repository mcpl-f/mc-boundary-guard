package me.fulcanelly.tgbridge.boundaryguard.services.restrictions;

import java.util.List;

import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionReason;

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Coordinates the access {@link Condition} with the configured {@link Restriction}
 * strategies.
 *
 * {@link Restriction#refresh(Player, boolean)} is triggered both as a side effect of
 * {@link #allowsMovement} / {@link #allowsContainerUse} (so a stateful strategy like
 * Adventure Mode stays in sync with every gameplay check) and independently on join
 * and on a periodic timer, since a restricted player can go a while without
 * triggering any check yet still needs the restriction applied or lifted.
 */
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
        // Still applies every strategy's current-position side effects (e.g. a
        // gamemode switch), but the gate below is evaluated at the DESTINATION, not
        // the player's current spot. A location-dependent condition (in-spawn-radius)
        // can be true right now and false one step later; short-circuiting on the
        // current position would let that one crossing step through unchecked.
        refresh(player);
        if (accessCondition.isMet(player, destination)) {
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

    /**
     * Whether this player is currently restricted, without applying anything.
     *
     * Unlike {@link #refresh}, this never touches a {@link Restriction} strategy -
     * safe to call from a listener that may itself be running nested inside a
     * strategy's own side effect (e.g. a {@code PlayerGameModeChangeEvent} fired
     * from inside {@code AdventureModeRestriction}'s own {@code setGameMode} call).
     * Calling {@link #refresh} there would re-enter that same strategy and, since
     * the event fires before the mode is actually applied, loop forever.
     */
    public boolean isRestricted(Player player) {
        return !accessCondition.isMet(player);
    }

    /** What this player still needs to satisfy, for a player-facing reminder message. */
    public List<ConditionReason> unmetReasons(Player player) {
        return accessCondition.unmetReasons(player);
    }

    // InventoryType has no built-in "is a container" predicate, so the block-like
    // types this restriction cares about are listed explicitly.
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
