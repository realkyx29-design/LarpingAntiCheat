package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.physics.MovementSnapshot;

/**
 * Blink / freecam / teleport-ahead detection.
 *
 * <p>A legitimate player moves only a few blocks per packet. A blink hack holds
 * position packets then releases them (large single-move delta); freecam and
 * teleport-ahead produce the same signature. Server-initiated teleports set
 * teleport grace and are ignored; riptide/elytra/explosions have their own
 * envelopes. Only displacements no single-tick latency coalescing could produce
 * are flagged, with bounded ping slack, so laggy players never false-positive.
 */
public final class BlinkCheck extends MovementCheck {

    public BlinkCheck() {
        super("Blink");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;
        MovementSnapshot s = ctx.move();
        if (s == null) return;

        // Server teleports (plugs/portals/commands) are graceful and rebase.
        if (ctx.data().inTeleportGrace()) return;
        // Self-propelled launches.
        if (s.riptide || s.gliding || s.velocityH > 1.0 || s.hasVelocity) return;

        CheckConfig cc = ctx.cfg().check("blink");
        double maxH = cc.v1() > 0 ? cc.v1() : 6.0;
        double maxV = cc.v2() > 0 ? cc.v2() : 5.0;

        if (ctx.cfg().compensatePing() && ctx.ping() > 150) {
            maxH += Math.min(3.0, (ctx.ping() - 150) / 200.0);
        }

        double h = s.hSpeed;
        double v = Math.abs(s.deltaY);
        boolean violation = h > maxH || v > maxV;

        if (violation) {
            double excess = Math.max(h - maxH, v - maxV);
            double confidence = Math.min(0.99, 0.85 + excess * 0.02);
            ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                    Math.min(1.0, 0.6 + excess * 0.08), confidence,
                    "hDelta=" + f(h) + " vDelta=" + f(s.deltaY) + " maxH=" + f(maxH)
                            + " ping=" + ctx.ping(),
                    me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            ctx.data().adjustBuffer("blink", 1.0, 32.0);
        } else {
            ctx.data().adjustBuffer("blink", -1.0, 32.0);
        }
    }

    private static String f(double d) {
        return String.format("%.2f", d);
    }
}
