package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.physics.MovementSnapshot;

/**
 * Step / auto-step / high-jump detection.
 *
 * <p>Vanilla auto-step rises ~0.6 blocks per move at most, and a normal jump
 * leaves the ground (feet off the ground) before rising. A Step hack raises
 * the player ~1 block in a single move while staying "grounded". We flag a
 * grounded upward delta beyond the step limit, with carve-outs for jump boost,
 * slime/bed bounces, climbing, liquids, gliding, riptide and knockback.
 */
public final class StepCheck extends MovementCheck {

    public StepCheck() {
        super("Step");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;
        MovementSnapshot s = ctx.move();
        if (s == null) return;

        if (s.gliding || s.riptide || s.hasVelocity || s.onBouncy || s.levitation
                || s.onClimbable || s.feetInLiquid || s.inWeb || s.inLava) {
            decay(ctx); return;
        }
        if (s.jumpAmplifier >= 0) { decay(ctx); return; }

        CheckConfig cc = ctx.cfg().check("step");
        double maxStep = cc.v1() > 0 ? cc.v1() : 0.85;
        double minConfirm = cc.v2() > 0 ? cc.v2() : 2.0;

        // Cheap exit for normal/downward motion.
        if (s.deltaY <= maxStep) { decay(ctx); return; }

        // A genuine jump: airborne at this sample. A step hack is a GROUNDED
        // rise (feet stay at ground level).
        boolean groundedRise = s.serverGround;

        if (groundedRise) {
            double buf = ctx.data().adjustBuffer("step", 1.0, 32.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                        0.55, 0.9,
                        "grounded dY=" + f(s.deltaY) + " hSpeed=" + f(s.hSpeed)
                                + " below=" + s.belowBlock.getType().name().toLowerCase()
                                + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            }
        } else {
            decay(ctx);
        }
    }

    private void decay(CheckContext ctx) {
        ctx.data().adjustBuffer("step", -2.0, 32.0);
    }

    private static String f(double d) {
        return String.format("%.3f", d);
    }
}
