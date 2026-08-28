package me.larping.anticheat.managers;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Per-tick flight-authority enforcement.
 *
 * <p>The server is the single authority for who may fly. A player is allowed to
 * fly only when {@code getAllowFlight()} is true (OPs and anyone granted flight
 * by the server) or in Spectator mode. Being in Creative is not enough by
 * itself for a non-OP player: if they are flying while the server never
 * enabled flight and they are not elytra-gliding/levitating, that is
 * unauthorized flight.
 *
 * <p>This never rubber-bands movement (no teleport), so it cannot cause the
 * false setbacks players feel from the movement checks. By default it logs the
 * violation; if flight enforcement is enabled in config it also cleanly disables
 * the illegal flight state ({@code setAllowFlight(false)} / {@code setFlying(false)})
 * so the cheat cannot continue flying, without moving the player.
 */
public final class FlightEnforcer {

    private final LarpingAntiCheat plugin;

    public FlightEnforcer(LarpingAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void tick(Player player) {
        try {
            PlayerData data = plugin.data(player);

            // Grace / fully exempt (OP, spectator, bypass permission, login).
            GameMode gm = player.getGameMode();
            if (gm == GameMode.SPECTATOR) return;
            if (isOp(player) || hasBypass(player)) {
                // Trusted players flying in creative must keep flight enabled.
                if (gm == GameMode.CREATIVE && !player.getAllowFlight()) {
                    try { player.setAllowFlight(true); } catch (Throwable ignored) { }
                }
                return;
            }
            if (data.inHardGrace()) return;

            if (!CheckContext.isUnauthorizedFlying(player)) return;

            // Sustained: count it (avoid one-off jitter).
            int ticks = data.incrementFlyViolation();
            if (ticks < 6) return;

            // Always log the skid line.
            try {
                plugin.notifier().logFlag(player, "Fly", "Movement", ticks, 0.95,
                        "unauthorized flight in gamemode=" + gm
                                + " allowFlight=" + player.getAllowFlight()
                                + " op=" + isOp(player) + " ticks=" + ticks);
            } catch (Throwable ignored) { }

            // Optionally stop the illegal flight (no teleport). Off by default.
            if (plugin.configManager().get().enforceFlight()) {
                try {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) {
            // Never throw out of the per-tick loop.
        }
    }

    private static boolean isOp(Player player) {
        try { return player.isOp(); } catch (Throwable t) { return false; }
    }

    private static boolean hasBypass(Player player) {
        try {
            return player.hasPermission("hyphon.bypass") || player.hasPermission("lac.bypass");
        } catch (Throwable t) {
            return false;
        }
    }
}
