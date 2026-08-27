package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Anti-knockback / velocity check.
 *
 * <p>When the server applies knockback it records the expected velocity
 * vector (from {@link org.bukkit.event.entity.EntityVelocityEvent} /
 * {@code PlayerKnockbackEvent}). A few ticks later the player's actual
 * displacement must reflect it. If the observed horizontal movement is a
 * small fraction of what physics predicted — and the player wasn't blocked by
 * a wall, in liquid, in a cobweb or on the ground in a corner — the client is
 * cancelling velocity.
 *
 * <p>This replaces the old blanket "velocity grace" which let any player
 * shrug off knockback with zero scrutiny.
 */
public final class NoKnockbackCheck extends MovementCheck {

    public NoKnockbackCheck() {
        super("NoKnockback");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        if (ctx.isMovementExempt() || !checkEnabled(ctx)) return;

        CheckConfig cc = ctx.cfg().check("noknockback");
        double minRatio = cc.v1() > 0 ? cc.v1() : 0.35;
        double minConfirm = cc.v2() > 0 ? cc.v2() : 3.0;

        // Measure accumulated displacement against physics expectation.
        Double ratio = ctx.data().consumeKnockbackObservation();
        if (ratio == null) return; // no pending knockback yet

        Location loc = player.getLocation();
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 0.9, 0).getBlock();
        boolean obstructed = feet.getType().name().contains("COBWEB")
                || feet.isLiquid() || head.isLiquid();

        // Ration observed vs expected. Low ratio AND not physically blocked.
        boolean resisted = ratio < minRatio && !obstructed;

        if (resisted) {
            double buf = ctx.data().adjustBuffer("noknockback", 1.0, 32.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(player, checkName, "Movement",
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
