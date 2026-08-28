package me.larping.anticheat.checks;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.config.ConfigManager;
import me.larping.anticheat.data.PlayerData;
import me.larping.anticheat.physics.MovementSnapshot;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Shared context for check execution.
 *
 * <p>Holds the immutable config snapshot so checks never perform YAML lookups
 * in the hot path, plus shared exemption logic. Movement checks receive a
 * {@link MovementSnapshot} (built once per move event) through {@link #move()};
 * combat/world checks leave that {@code null}.
 */
public final class CheckContext {

    private final LarpingAntiCheat plugin;
    private final Player player;
    private final PlayerData data;
    private final MovementSnapshot move;

    public CheckContext(LarpingAntiCheat plugin, Player player, PlayerData data) {
        this(plugin, player, data, null);
    }

    public CheckContext(LarpingAntiCheat plugin, Player player, PlayerData data, MovementSnapshot move) {
        this.plugin = plugin;
        this.player = player;
        this.data = data;
        this.move = move;
    }

    public LarpingAntiCheat plugin() { return plugin; }
    public Player player() { return player; }
    public PlayerData data() { return data; }
    /** Per-move physics snapshot; {@code null} for non-movement (event) checks. */
    public MovementSnapshot move() { return move; }

    public ConfigManager.Snapshot cfg() {
        return plugin.configManager().get();
    }

    public double tps() {
        return plugin.tps();
    }

    /** Exponentially-smoothed ping (updated each tick) so lag spikes don't
     *  cause threshold flapping. */
    public int ping() {
        return data.smoothPing();
    }

    /**
     * True when the player should be completely ignored by checks: bypass
     * permission, creative/spectator, dead/ghost, or in a hard grace window
     * (login / teleport / respawn / world change).
     *
     * <p>Damage and knockback are deliberately NOT blanket exemptions — they
     * used to make players permanently un-checkable (e.g. standing in fire).
     * They are compensated individually by the movement checks instead.
     */
    public boolean isFullyExempt() {
        GameMode gm = player.getGameMode();
        if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) return true;
        if (player.isDead() || !player.isValid()) return true;
        if (player.hasPermission(cfg().exemptPermission())) return true;
        return data.inHardGrace();
    }

    /** Movement checks additionally skip vehicles and server-granted flight. */
    public boolean isMovementExempt() {
        if (isFullyExempt()) return true;
        if (player.isInsideVehicle()) return true;
        if (player.isFlying()) return true; // server-authoritative flight (donor/creative)
        return false;
    }

    /**
     * Movement checks that reason about airborne physics should skip when the
     * player is in a state the check cannot model (glide, levitation, riptide,
     * liquid, cobweb, climbable) — each has its own dedicated handling or is
     * legitimate. This prevents false positives while leaving those states
     * covered by the right check (speed/jesus/spider/etc).
     */
    public boolean isPhysicsExempt() {
        MovementSnapshot s = move;
        if (s == null) return false;
        return s.gliding || s.levitation || s.riptide || s.inLava
                || s.feetInLiquid || s.headInLiquid || s.inWeb || s.onClimbable;
    }
}
