package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.Check;
import me.larping.anticheat.checks.CheckContext;
import org.bukkit.entity.Player;

/** Base type for movement checks; mechanics and grace periods belong in the shared context. */
public abstract class MovementCheck implements Check {
    protected final String path;
    protected MovementCheck(String path) { this.path = path; }
    @Override public boolean enabled() { return true; }
    protected boolean exempt(Player player) {
        return player.isInsideVehicle() || player.isFlying();
    }
    @Override public abstract void evaluate(Player player, CheckContext context);
}
