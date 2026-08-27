package me.larping.anticheat.checks;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/** Shared context keeps checks independent of listeners and makes future checks testable. */
public record CheckContext(LarpingAntiCheat plugin, Player player, PlayerData data) {
    public double tps() { return plugin.tps(); }
    public int ping() { return player.getPing(); }
}
