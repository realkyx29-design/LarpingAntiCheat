package me.larping.anticheat.checks;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.config.ConfigManager;
import me.larping.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Shared context for check execution.
 *
 * <p>Holds the immutable config snapshot so checks never perform YAML lookups
 * in the hot path, plus shared exemption logic.
 */
public record CheckContext(LarpingAntiCheat plugin, Player player, PlayerData data) {

    public ConfigManager.Snapshot cfg() {
        return plugin.configManager().get();
    }

    public double tps() {
        return plugin.tps();
    }

    public int ping() {
        return player.getPing();
    }

    /**
     * True when the player should be completely ignored by checks:
     * bypass permission, creative/spectator, dead, or in a hard grace
     * window (login / teleport / respawn / world change).
     *
     * <p>Note: damage and knockback are NOT in here anymore. Those used to
     * grant a blanket bypass (fire ticks = permanent god mode), they are now
     * compensated individually by the movement checks.
     */
    public boolean isFullyExempt() {
        GameMode gm = player.getGameMode();
        if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) return true;
        if (player.isDead() || !player.isValid()) return true;
        if (player.hasPermission(cfg().exemptPermission())) return true;
        return data.inHardGrace();
    }

    /** Movement checks additionally skip vehicles and real flight. */
    public boolean isMovementExempt() {
        if (isFullyExempt()) return true;
        if (player.isInsideVehicle()) return true;
        // isFlying() is only true when the server itself has flight enabled
        // (donor flight / creative flight managed by the server).
        if (player.isFlying()) return true;
        return false;
    }
}
