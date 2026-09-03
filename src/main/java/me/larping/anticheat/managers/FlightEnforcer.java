package me.larping.anticheat.managers;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Per-tick flight-authority enforcement.
 *
 * <p>The plugin — not the client — decides who may fly. Authorised fliers are
 * OPs, Spectators, or players with an explicit fly/bypass permission
 * (see {@link CheckContext#serverAllowsFlight}). A non-OP Creative player who
 * is flying without authorisation (e.g. de-opped while still flying) is
 * handled deterministically every tick:
 *
 * <ol>
 *   <li>the violation is logged (skid line) and accumulates a short
 *       confirmation buffer (ignores one-off jitter / lag),</li>
 *   <li>flight is disabled for the client ({@code setAllowFlight(false)} /
 *       {@code setFlying(false)}) so vanilla stops them,</li>
 *   <li>if {@code enforce-flight} is on and the player is still airborne, they
 *       are pulled back to their last server-valid grounded position — this
 *       works even in Creative and never relies on the heuristic movement
 *       checks (which stay log-only to avoid false rubber-banding).</li>
 * </ol>
 *
 * Elytra gliding, levitation, vehicles, lag spikes and server
 * teleports/grace never trigger it.
 */
public final class FlightEnforcer {

    private final LarpingAntiCheat plugin;

    public FlightEnforcer(LarpingAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void tick(Player player) {
        try {
            PlayerData data = plugin.data(player);
            GameMode gm = player.getGameMode();

            if (gm == GameMode.SPECTATOR) { data.resetFlyViolation(); return; }

            // Authorised fliers: keep their flight state healthy and clear
            // any accumulated violation.
            if (CheckContext.serverAllowsFlight(player)) {
                data.resetFlyViolation();
                return;
            }
            // Grace / lag / elytra / levitation / vehicle: not a flight cheat.
            if (data.inHardGrace()) { data.resetFlyViolation(); return; }
            if (isLaggy(player)) { data.resetFlyViolation(); return; }
            if (player.isGliding()
                    || player.hasPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION)
                    || player.isInsideVehicle()) {
                data.resetFlyViolation();
                return;
            }

            if (!player.isFlying()) {
                data.resetFlyViolation();
                return;
            }

            int ticks = data.incrementFlyViolation();
            // A few-tick confirmation buffer so a brief packet re-order or a
            // post-deop client catch-up isn't punished instantly.
            if (ticks < 3) return;

            // Always log.
            plugin.notifier().logFlag(player, "Fly", "Movement", ticks, 0.97,
                    "unauthorized flight gamemode=" + gm
                            + " op=" + player.isOp()
                            + " allowFlight=" + player.getAllowFlight()
                            + " ticks=" + ticks);

            // Deterministic enforcement (on by default for flight authority —
            // this is not the heuristic movement-correction toggle).
            // 1) Turn off the client's flight capability (stops creative fly).
            try {
                player.setAllowFlight(false);
                player.setFlying(false);
            } catch (Throwable ignored) { }

            if (plugin.configManager().get().enforceFlight() && ticks >= 3) {
                // 2) Pull back to the last verified grounded position so the
                //    client cannot simply keep ascending. This is the only
                //    "tp back" and it targets a definite, server-authorised
                //    illegal state (flying with no permission) — never normal
                //    walking/elytra/lag, which all return above.
                Location safe = data.safeLocation();
                if (safe != null && safe.getWorld() != null
                        && safe.getWorld().equals(player.getWorld())) {
                    Location corrected = safe.clone();
                    corrected.setYaw(player.getLocation().getYaw());
                    corrected.setPitch(player.getLocation().getPitch());
                    try {
                        player.teleport(corrected);
                        player.setVelocity(new Vector(0, 0, 0));
                        player.setFallDistance(0);
                    } catch (Throwable ignored) { }
                }
            }
        } catch (Throwable ignored) {
            // Never break the main tick loop.
        }
    }

    private boolean isLaggy(Player player) {
        try {
            double ping = plugin.data(player).smoothPing();
            double tps = plugin.tps();
            return ping > plugin.configManager().get().lagCompensationPing()
                    || (plugin.configManager().get().lagCompensationTps() > 0
                        && tps < plugin.configManager().get().lagCompensationTps());
        } catch (Throwable t) {
            return true; // on any doubt, don't punish
        }
    }
}
