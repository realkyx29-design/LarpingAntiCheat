package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import org.bukkit.entity.Player;

/**
 * Blink / freecam / teleport-ahead detection.
 *
 * <p>A legitimate player moves at most a few blocks per packet. A blink hack
 * holds position packets then releases them, and a freecam detach is followed
 * by the real body snapping far forward. Server-initiated teleports fire
 * {@link org.bukkit.event.player.PlayerTeleportEvent} and set teleport grace;
 * this check ignores that window, so the only large single-move deltas left
 * are cheat-originated.
 *
 * <p>The old "Freecam" check flagged a sneaking player more than 10 blocks
 * from their previous position — but freecam never moves the body, so it
 * could never fire; it also missed actual blink entirely.
 */
public final class BlinkCheck extends MovementCheck {

    public BlinkCheck() {
        super("Blink");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;

        // Server teleports (plugs, portals, commands) are graceful.
        if (ctx.data().inTeleportGrace()) return;

        CheckConfig cc = ctx.cfg().check("blink");
        double maxH = cc.v1() > 0 ? cc.v1() : 6.0;
        double maxV = cc.v2() > 0 ? cc.v2() : 5.0;

        double hSpeed = ctx.data().horizontalSpeed();
        double dY = ctx.data().deltaY();

        // Legit launchers: riptide / elytra / explosion velocity.
        if (ctx.data().inRiptideGrace() || player.isGliding()
                || ctx.data().expectedVelocityHorizontal() > 1.0) {
            return;
        }
        // Ping bursts can coalesce a few ticks of motion — only flag
        // displacements that no single-tick coalescing could produce.
        if (ctx.cfg().compensatePing() && ctx.ping() > 150) {
            maxH += Math.min(3.0, (ctx.ping() - 150) / 200.0);
        }

        boolean violation = hSpeed > maxH || Math.abs(dY) > maxV;
        if (violation) {
            double excess = Math.max(hSpeed - maxH, Math.abs(dY) - maxV);
            double confidence = Math.min(0.99, 0.85 + excess * 0.02);
            ctx.plugin().violations().flag(player, checkName, "Movement",
                    Math.min(1.0, 0.6 + excess * 0.08), confidence,
                    "hDelta=" + String.format("%.2f", hSpeed)
                            + " vDelta=" + String.format("%.2f", dY)
                            + " maxH=" + String.format("%.1f", maxH)
                            + " ping=" + ctx.ping(),
                    me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            ctx.data().adjustBuffer("blink", 1.0, 32.0);
        } else {
            ctx.data().adjustBuffer("blink", -1.0, 32.0);
        }
    }
}
