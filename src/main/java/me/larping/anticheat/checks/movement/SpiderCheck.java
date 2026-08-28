package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.physics.MovementSnapshot;

/**
 * Spider / wall-climb detection.
 *
 * <p>Without a climbable block a player cannot rise while touching only a wall.
 * We track sustained upward motion while a wall is adjacent to the upper body
 * (real collision shapes), excluding ladders/vines/scaffolding, jump boost,
 * levitation, gliding, riptide and active knockback. Requires sustained ticks
 * and a confirmation buffer to avoid false flags on jump-pearl/bounce edges.
 */
public final class SpiderCheck extends MovementCheck {

    public SpiderCheck() {
        super("Spider");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;
        MovementSnapshot s = ctx.move();
        if (s == null) return;

        if (s.onClimbable || s.levitation || s.gliding || s.riptide
                || s.feetInLiquid || s.inWeb || s.onBouncy) {
            ctx.data().adjustBuffer("spider", -2.0, 64.0);
            return;
        }
        if (s.jumpAmplifier >= 0) { ctx.data().adjustBuffer("spider", -2.0, 64.0); return; }

        CheckConfig cc = ctx.cfg().check("spider");
        double minConfirm = Math.max(cc.v1() > 0 ? cc.v1() : 6.0, cc.v2() > 0 ? cc.v2() : 10.0);

        boolean airborne = !s.serverGround;
        boolean rising = s.deltaY > 0.09;
        boolean spider = airborne && rising && s.touchingWall && !s.hasVelocity && s.hSpeed < 0.45;

        if (spider) {
            double buf = ctx.data().adjustBuffer("spider", 1.0, 64.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                        0.55, 0.92,
                        "climb dY=" + f(s.deltaY) + " hSpeed=" + f(s.hSpeed) + " air=" + s.airTicks
                                + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            }
        } else {
            ctx.data().adjustBuffer("spider", -2.0, 64.0);
        }
    }

    private static String f(double d) {
        return String.format("%.3f", d);
    }
}
