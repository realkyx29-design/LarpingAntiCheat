package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.Check;
import me.larping.anticheat.checks.CheckContext;
import org.bukkit.entity.Player;

/**
 * Base class for movement-related checks (Speed, Fly, etc.).
 * Handles shared checks for vehicles, spectators, and grace periods.
 */
public abstract class MovementCheck implements Check {
    protected final String checkName;

    protected MovementCheck(String checkName) {
        this.checkName = checkName;
    }

    @Override
    public String name() {
        return checkName;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    protected boolean shouldBypass(Player player, CheckContext context) {
        return player.isInsideVehicle() ||
               player.isFlying() ||
               player.getGameMode() == org.bukkit.GameMode.SPECTATOR ||
               player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
               context.data().isGraceful();
    }

    @Override
    public abstract void evaluate(Player player, CheckContext context);
}
