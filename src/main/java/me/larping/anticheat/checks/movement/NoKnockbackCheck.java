package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.physics.MovementSnapshot;

/**
 * Anti-knockback / velocity check.
 *
 * <p>When the server applies knockback (combat hit / explosion / projectile —
 * captured via {@code PlayerVelocityEvent} right after an
 * {@code EntityDamageEvent}), the expected velocity is recorded. A few ticks
 * later the player's accumulated displacement must reflect it. If observed
 * horizontal movement is a small fraction of physics prediction, the client is
 * cancelling velocity. Physical reasons for not moving — cobweb, liquid,
 * climbing, or being backed into a wall — are exempt so legitimate stuck hits
 * never flag.
 */
public final class NoKnockbackCheck extends MovementCheck {

    public NoKnockbackCheck() {
        super("NoKnockback");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        if (ctx.isMovementExempt() || !checkEnabled(ctx)) return;

        Double ratio = ctx.data().consumeKnockbackObservation();
        if (ratio == null) return; // no pending/ready knockback

        CheckConfig cc = ctx.cfg().check("noknockback");
        double minRatio = cc.v1() > 0 ? cc.v1() : 0.35;
        double minConfirm = cc.v2() > 0 ? cc.v2() : 3.0;

        MovementSnapshot s = ctx.move();
        boolean physicallyBlocked = false;
        if (s != null) {
            physicallyBlocked = s.inWeb || s.feetInLiquid || s.onClimbable
                    || s.touchingWall || s.sneaking;
        }

        boolean resisted = ratio < minRatio && !physicallyBlocked;

        if (resisted) {
            double buf = ctx.data().adjustBuffer("noknockback", 1.0, 32.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                        0.6, 0.85,
                        "velocityRatio=" + String.format("%.2f", ratio)
                                + " (< " + minRatio + ") buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.NONE);
            }
        } else {
            ctx.data().adjustBuffer("noknockback", -2.0, 32.0);
        }
    }
}
