package me.larping.anticheat.checks;

import org.bukkit.entity.Player;

/**
 * Interface representing an observable check in LarpingAntiCheat.
 * Designed to be modular, conservative, and compatible with custom SMP mechanics.
 */
public interface Check {
    String name();
    boolean enabled();
    void evaluate(Player player, CheckContext context);
}
