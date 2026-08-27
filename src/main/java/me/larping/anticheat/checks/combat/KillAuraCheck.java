package me.larping.anticheat.checks.combat;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * KillAura / aim-assist detection.
 *
 * <p>The old Rotation check only verified the pitch range (-90..90), which the
 * vanilla client itself never violates — it detected nothing. This check
 * combines two real signals:
 *
 * <ol>
 *   <li><b>Attack FOV</b>: when a player lands a hit, the target must be in
 *       roughly the direction the player is facing (within their view
 *       frustum plus latency slack). Auras attack entities behind the player.</li>
 *   <li><b>Rotation snaps</b>: aim bots produce near-instant yaw jumps between
 *       targets, far beyond human flick speed on sustained attacks. We track
 *       large yaw deltas immediately followed by a hit.</li>
 * </ol>
 *
 * Also retains the impossible-pitch sanity check as a high-confidence signal.
 */
public final class KillAuraCheck extends CombatCheck {

    public KillAuraCheck() {
        super("KillAura");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;

        // Impossible pitch is a hard protocol-level signal.
        float pitch = player.getLocation().getPitch();
        if (pitch > 90.0f || pitch < -90.0f) {
            flag(ctx, 1.0, 0.99, "invalidPitch=" + pitch);
        }
    }

    public void evaluateAttack(Player attacker, LivingEntity target, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;
        CheckConfig cc = ctx.cfg().check("killaura");

        Location eye = attacker.getEyeLocation();
        Location targetLoc = target.getEyeLocation();
        if (!eye.getWorld().equals(targetLoc.getWorld())) return;

        // --- 1) Angle between look vector and vector-to-target -------------
        Vector look = eye.getDirection().normalize();
        Vector toTarget = targetLoc.toVector().subtract(eye.toVector());
        if (toTarget.lengthSquared() < 1.0e-6) return;
        toTarget.normalize();
        double dot = Math.max(-1.0, Math.min(1.0, look.dot(toTarget)));
        double angle = Math.toDegrees(Math.acos(dot));

        // Vanilla will only register an attack you can reasonably face; allow
        // generous slack for lag and hitbox edge (~60deg covers corner hits).
        double maxAngle = 62.0;
        if (ctx.cfg().compensatePing() && ctx.ping() > 100) {
            maxAngle += Math.min(20.0, (ctx.ping() - 100) / 15.0);
        }

        if (angle > maxAngle) {
            double buf = ctx.data().adjustBuffer("killaura", 1.0, 32.0);
            if (buf >= cc.v2()) {
                double confidence = Math.min(0.97, 0.75 + (angle - maxAngle) / 120.0);
                flag(ctx, 0.8, confidence,
                        "attackAngle=" + String.format("%.1f", angle)
                                + " max=" + String.format("%.1f", maxAngle)
                                + " buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("killaura", -1.0, 32.0);
        }

        // --- 2) Multi-target in one second (single/multi aura) ------------
        int targets = ctx.data().distinctTargetsLastSecond();
        if (targets >= 4) {
            flag(ctx, 0.7, 0.82, "distinctTargets/sec=" + targets);
        }
    }

    /**
     * Called by the movement listener to record rotation snaps. A yaw delta
     * exceeding human flick speed only matters when it immediately precedes an
     * attack, so the attack handler checks {@link PlayerData#lastYaw()}.
     */
    public void recordRotation(Player player, CheckContext ctx, float oldYaw, float newYaw) {
        // The attack-time check uses the most recent yaw delta; store a flag
        // via the data object's transient fields if a huge snap occurred.
        float snap = Math.abs(PlayerData.yawDelta(newYaw, oldYaw));
        if (snap > ctx.cfg().check("killaura").v1()) {
            // A snap followed by an attack within ~2 packets is suspicious.
            ctx.data().recentSnapMs = System.currentTimeMillis();
            ctx.data().recentSnapDegrees = snap;
        }
    }

    /** Hook invoked by the attack listener right after an attack lands. */
    public void evaluateSnapOnAttack(CheckContext ctx) {
        long since = System.currentTimeMillis() - ctx.data().recentSnapMs;
        if (since < 120 && ctx.data().recentSnapDegrees > ctx.cfg().check("killaura").v1() + 25.0) {
            flag(ctx, 0.6, 0.78,
                    "snapOnAttack=" + String.format("%.1f", ctx.data().recentSnapDegrees) + "deg "
                            + String.format("%.0f", since) + "ms");
        }
    }
}
