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
 * <p>Multiple independent, low-false-positive signals (a single benign lag
 * event never flags):
 * <ol>
 *   <li><b>Hit from behind</b>: the target is well outside the player's view
 *       frustum (>110° — i.e. effectively behind the attacker) on a landed
 *       hit. A genuine melee hit always faces the target; generous slack
 *       covers target movement during latency. Requires repeated confirmations.</li>
 *   <li><b>Rotation snap → immediate hit</b>: a near-instant, super-human yaw
 *       jump immediately followed by a hit (aimbot lock).</li>
 *   <li><b>Multi-target</b>: hitting 4+ distinct entities within one second
 *       (single/multi-aura).</li>
 *   <li><b>Impossible pitch</b>: protocol-level sanity (client never exceeds
 *       ±90°) — high confidence.</li>
 * </ol>
 */
public final class KillAuraCheck extends CombatCheck {

    public KillAuraCheck() {
        super("KillAura");
    }

    /** Protocol-level sanity check, safe to run every event. */
    @Override
    public void evaluate(CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;
        float pitch = ctx.player().getLocation().getPitch();
        if (pitch > 90.0f || pitch < -90.0f) {
            flag(ctx, 1.0, 0.99, "invalidPitch=" + pitch);
        }
    }

    public void evaluateAttack(Player attacker, LivingEntity target, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;
        CheckConfig cc = ctx.cfg().check("killaura");

        Location eye = attacker.getEyeLocation();
        Location targetLoc = target.getEyeLocation();
        if (eye.getWorld() == null || !eye.getWorld().equals(targetLoc.getWorld())) return;

        // --- 1) Angle between look vector and vector-to-target -------------
        Vector look = eye.getDirection().normalize();
        Vector toTarget = targetLoc.toVector().subtract(eye.toVector());
        if (toTarget.lengthSquared() < 1.0e-6) return;
        toTarget.normalize();
        double dot = Math.max(-1.0, Math.min(1.0, look.dot(toTarget)));
        double angle = Math.toDegrees(Math.acos(dot));

        // A landed melee hit means the server already saw the player in range.
        // Only flag when the target is effectively BEHIND the attacker
        // (>110°), which no legit hit (even with target drift) produces.
        double maxAngle = 110.0;
        if (ctx.cfg().compensatePing() && ctx.ping() > 100) {
            maxAngle += Math.min(15.0, (ctx.ping() - 100) / 20.0);
        }

        if (angle > maxAngle) {
            double buf = ctx.data().adjustBuffer("killaura", 1.0, 32.0);
            if (buf >= Math.max(3, cc.v2() + 1)) {
                double confidence = Math.min(0.97, 0.80 + (angle - maxAngle) / 180.0);
                flag(ctx, 0.8, confidence,
                        "hitFromBehind angle=" + String.format("%.1f", angle)
                                + " max=" + String.format("%.1f", maxAngle)
                                + " buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("killaura", -1.0, 32.0);
        }

        // --- 2) Multi-target in one second (single/multi aura) ------------
        int targets = ctx.data().distinctTargetsLastSecond();
        if (targets >= 5) {
            flag(ctx, 0.7, 0.85, "distinctTargets/sec=" + targets);
        }
    }


    /**
     * Hard-impossible test for the CURRENT hit: target more than ~150° off the
     * player's view (physically behind), even after generous ping slack. A
     * legit hit can never land; the listener cancels this hit immediately.
     */
    public boolean isHitImpossible(Player attacker, LivingEntity target, CheckContext ctx) {
        try {
            Location eye = attacker.getEyeLocation();
            Location tl = target.getEyeLocation();
            if (eye.getWorld() == null || !eye.getWorld().equals(tl.getWorld())) return false;
            Vector look = eye.getDirection().normalize();
            Vector to = tl.toVector().subtract(eye.toVector());
            if (to.lengthSquared() < 1.0e-6) return false;
            to.normalize();
            double dot = Math.max(-1.0, Math.min(1.0, look.dot(to)));
            double angle = Math.toDegrees(Math.acos(dot));
            // A genuine melee hit is within the view frustum (< ~90 deg),
            // even accounting for target drift during latency. Hits beyond
            // ~120 deg mean the target is at/behind the player's side — a
            // killaura locking on off-screen. Allow a little ping slack.
            double hardLimit = 120.0;
            if (ctx.cfg().compensatePing()) hardLimit += Math.min(15.0, ctx.ping() / 30.0);
            return angle > hardLimit;
        } catch (Throwable t) { return false; }
    }

    /** Records rotation snaps from the movement listener (main thread). */
    public void recordRotation(Player player, CheckContext ctx, float oldYaw, float newYaw) {
        float snap = Math.abs(PlayerData.yawDelta(newYaw, oldYaw));
        if (snap > ctx.cfg().check("killaura").v1()) {
            ctx.data().recentSnapMs = System.currentTimeMillis();
            ctx.data().recentSnapDegrees = snap;
        }
    }

    /** Invoked by the attack listener right after a hit lands. */
    public void evaluateSnapOnAttack(CheckContext ctx) {
        long since = System.currentTimeMillis() - ctx.data().recentSnapMs;
        // A huge yaw snap followed within ~120ms by a hit = aimbot lock-on.
        if (since < 120 && ctx.data().recentSnapDegrees > ctx.cfg().check("killaura").v1() + 25.0) {
            flag(ctx, 0.5, 0.78,
                    "snapOnAttack=" + String.format("%.1f", ctx.data().recentSnapDegrees) + "deg "
                            + String.format("%.0f", since) + "ms");
        }
    }
}
