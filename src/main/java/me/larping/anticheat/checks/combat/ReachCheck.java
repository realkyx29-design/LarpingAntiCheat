package me.larping.anticheat.checks.combat;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Attack reach check measured eye-to-hitbox.
 *
 * <p>The old check measured feet-to-feet 3D distance, which gave huge
 * false-positives for targets slightly above/below the attacker and missed
 * angled hits. Reach is the distance from the attacker's eye to the closest
 * point of the target's server-side bounding box.
 *
 * <p>Vanilla survival reach is 3.0 blocks (interactive range); with latency
 * the client can hit targets that have since moved, so a bounded additive
 * ping allowance is applied. Lag/teleport grace is honoured.
 */
public final class ReachCheck extends CombatCheck {

    public ReachCheck() {
        super("Reach");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        // Attacks are reported via evaluateAttack.
    }

    public void evaluateAttack(Player attacker, LivingEntity target, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;
        if (attacker.isInsideVehicle()) return;

        CheckConfig cc = ctx.cfg().check("reach");

        Location eye = attacker.getEyeLocation();
        BoundingBox targetBox = target.getBoundingBox();
        if (eye.getWorld() == null || !eye.getWorld().equals(target.getWorld())) return;

        double distance = distanceEyeToBox(eye, targetBox);
        double maxDistance = cc.v1() > 0 ? cc.v1() : 3.05;

        if (ctx.cfg().compensatePing() && ctx.ping() > 60) {
            // Target may have moved toward/away in the latency window; a small
            // additive slack prevents false bans on laggy players.
            maxDistance += Math.min(0.6, (ctx.ping() - 60) / 350.0);
        }
        if (ctx.cfg().customModsEnabled() && ctx.cfg().customCombatComp()) {
            maxDistance += 0.15;
        }

        if (distance > maxDistance) {
            double excess = distance - maxDistance;
            double buf = ctx.data().adjustBuffer("reach", 1.0, 32.0);
            if (buf >= cc.v2()) {
                double confidence = Math.min(0.98, 0.75 + excess * 0.8);
                flag(ctx, Math.min(1.0, 0.5 + excess), confidence,
                        "dist=" + String.format("%.2f", distance)
                                + " max=" + String.format("%.2f", maxDistance)
                                + " ping=" + ctx.ping()
                                + " buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("reach", -2.0, 32.0);
        }
    }


    /**
     * Hard-impossible test for the CURRENT hit: target far beyond even the
     * ping/compat-adjusted reach (>1.5 blocks over), or in a different world.
     * The listener cancels this hit immediately (deterministic, no VL wait).
     */
    public boolean isHitImpossible(Player attacker, LivingEntity target, CheckContext ctx) {
        try {
            if (attacker.isInsideVehicle()) return false;
            Location eye = attacker.getEyeLocation();
            BoundingBox box = target.getBoundingBox();
            if (eye.getWorld() == null || !eye.getWorld().equals(target.getWorld())) return false;
            double distance = distanceEyeToBox(eye, box);
            // Vanilla survival melee can never connect past ~3.0 blocks.
            // Allow a clear, generous margin for latency/prediction (~0.8)
            // but anything beyond is an impossible hit: cancel it now.
            double hardLimit = 3.0 + 0.9;
            return distance > hardLimit;
        } catch (Throwable t) { return false; }
    }

    /** Distance from an eye location to the nearest point of an AABB. */
    private double distanceEyeToBox(Location eye, BoundingBox box) {
        double cx = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double cy = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double cz = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        Vector closest = new Vector(cx, cy, cz);
        return eye.toVector().distance(closest);
    }
}
