package me.larping.anticheat.checks;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * Shared context for check execution, supplying plugin reference, player, and player data.
 */
public record CheckContext(LarpingAntiCheat plugin, Player player, PlayerData data) {
    public double tps() {
        return plugin.tps();
    }

    public int ping() {
        return player.getPing();
    }

    public double getSensitivity(String checkName) {
        return plugin.getConfig().getDouble("checks." + checkName.toLowerCase() + ".sensitivity", 0.5);
    }
}
