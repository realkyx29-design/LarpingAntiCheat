package me.larping.anticheat.checks;

import org.bukkit.entity.Player;

/** A small extension point for server-observable checks. Checks must be conservative. */
public interface Check {
    String name();
    boolean enabled();
    void evaluate(Player player, CheckContext context);
}
