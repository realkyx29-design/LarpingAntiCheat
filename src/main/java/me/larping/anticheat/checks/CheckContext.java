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
     * True when the player is currently laggy (high smoothed ping) or the
     * server is running low TPS. Movement-correction / setback and any
     * movement-position enforcement must NEVER fire while this is true — lag
     * produces large apparent deltas that look like speed/fly but are not
     * cheats. Position checks stay lenient during lag; detection keeps
     * accumulating quietly but cannot teleport or punish a laggy player.
     */
    public boolean isLaggy() {
        int lagPing = Math.max(cfg().lagCompensationPing(), 0);
        double lagTps = cfg().lagCompensationTps() > 0 ? cfg().lagCompensationTps() : 19.0;
        return (lagPing > 0 && ping() > lagPing) || tps() < lagTps;
    }

    /**
     * Whether movement correction (snap-back) may be applied for this player
     * right now. Requires: enforcement enabled, not laggy, not OP, not a
     * legitimate fast/granted state. This is deliberately strict — a setback
     * should only ever happen for a clearly-impossible, non-lag move.
     */
    public boolean mayCorrectMovement() {
        if (!cfg().enforceCorrectMovement()) return false;
        if (isFullyExempt()) return false;
        if (isLaggy()) return false;          // never rubber-band a laggy player
        if (isPhysicsExempt()) return false;  // elytra/liquid/climb/riptide/levitation
        MovementSnapshot s = move;
        if (s != null && (s.gliding || s.riptide || s.hasVelocity
                || s.inWater || s.feetInLiquid || s.onClimbable || s.inWeb
                || s.serverGround == false && s.airTicks < 2)) {
            // gliding, knockback, water, ladders, webs and the first couple of
            // airborne ticks after leaving ground are never corrected.
            if (s == null) return true;
            if (s.gliding || s.riptide || s.hasVelocity || s.inWater
                    || s.feetInLiquid || s.headInLiquid || s.onClimbable || s.inWeb) return false;
        }
        return true;
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
        // Dynamic OP whitelist: read live server-operator state every time, so
        // a player who gains OP is immediately exempt and one who loses it is
        // immediately protected again. OPs accrue no VL, no setbacks, no bans.
        if (isOp(player)) return true;

        GameMode gm = player.getGameMode();
        if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) return true;
        if (player.isDead() || !player.isValid()) return true;
        // Both the new hyphon.bypass and legacy lac.bypass nodes.
        if (hasAny(player, cfg().exemptPermission(), "hyphon.bypass", "lac.bypass")) return true;
        return data.inHardGrace();
    }

    private static boolean isOp(Player player) {
        try {
            return player.isOp();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasAny(Player player, String... nodes) {
        for (String node : nodes) {
            if (node == null || node.isEmpty()) continue;
            try {
                if (player.hasPermission(node)) return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    /** Movement checks additionally skip vehicles and <b>server-granted</b>
     *  flight. A player who is flying WITHOUT server permission (survival
     *  fly hack: isFlying() true but getAllowFlight() false) is NOT exempt —
     *  that is the exact unauthorized flight the Fly check must catch. */
    public boolean isMovementExempt() {
        if (isFullyExempt()) return true;
        if (player.isInsideVehicle()) return true;
        try {
            // Only legitimate flight (creative/spectator/donor/plugin-granted)
            // is exempt. Flying without the server allowing it is a cheat.
            if (player.isFlying() && serverAllowsFlight(player)) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    /**
     * True when the player is actually permitted to fly, based on AUTHORISED
     * status rather than the gamemode alone. Vanilla Paper sets
     * {@code getAllowFlight()} true for everyone in Creative — including a
     * non-OP player who used to be OP — so that flag alone is not proof of
     * permission. Flight is authorised only when the player:
     * <ul>
     *   <li>is in Spectator (the server always flies spectators),</li>
     *   <li>is a live server operator ({@code isOp()}), or</li>
     *   <li>holds the bypass permission or a real fly permission
     *       (e.g. {@code essentials.fly}, {@code hyphon.fly}) <b>and</b> is in
     *       Creative (a player must already be able to have flight enabled
     *       for that permission to apply in survival).</li>
     * </ul>
     * A non-OP Creative player with no fly permission is therefore NOT
     * authorised, even though {@code getAllowFlight()} returns true.
     */
    public static boolean serverAllowsFlight(Player player) {
        try {
            GameMode gm = player.getGameMode();
            if (gm == GameMode.SPECTATOR) return true;
            if (player.isOp()) return true;

            boolean bypass = hasNode(player,
                    "hyphon.bypass", "lac.bypass",
                    "hyphon.fly", "essentials.fly", "essentialsf.fly",
                    "cmi.command.fly", "minecraft.commands.fly");
            // In survival a real flight permission (essentials/cmi) can
            // legitimately enable flight; in creative only an explicit fly
            // permission or OP authorises (so default non-OP creative is off).
            return bypass && (player.getAllowFlight());
        } catch (Throwable t) {
            // Fail open only on a genuine API error (not expected).
            return false;
        }
    }

    private static boolean hasNode(Player player, String... nodes) {
        for (String n : nodes) {
            try {
                if (n != null && player.hasPermission(n)) return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    /** True when flight is currently illegal for this player (flying without
     *  authorisation and not gliding/levitating/in a vehicle). */
    public static boolean isUnauthorizedFlying(Player player) {
        try {
            if (serverAllowsFlight(player)) return false;
            if (player.isGliding()) return false; // elytra
            if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION)) return false;
            if (player.isInsideVehicle()) return false;
            return player.isFlying();
        } catch (Throwable t) {
            return false;
        }
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
