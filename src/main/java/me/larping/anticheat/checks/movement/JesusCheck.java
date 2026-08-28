package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.physics.MovementSnapshot;
import me.larping.anticheat.util.CollisionUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Jesus / water-walk detection.
 *
 * <p>Vanilla: when crossing water you either fall in (feet inside the liquid,
 * horizontal speed drops to a crawl) or stand on a solid block under the
 * surface. A Jesus hack keeps the player at the surface at near-ground speed
 * with no collision beneath. We require sustained movement, liquid below, feet
 * NOT submerged, no real ground within reach, a level (non-diving) vertical
 * profile — and a confirmation buffer — so boats (vehicle), riptide, lily pads
 * and slab/stone under shallow water never false-flag.
 */
public final class JesusCheck extends MovementCheck {

    public JesusCheck() {
        super("Jesus");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;
        MovementSnapshot s = ctx.move();
        if (s == null) return;

        CheckConfig cc = ctx.cfg().check("jesus");
        double minConfirm = cc.v1() > 0 ? cc.v1() : 6.0;

        // Explicitly legit states.
        if (s.gliding || s.riptide || s.inLava || s.onClimbable || s.inWeb) {
            decay(ctx); return;
        }

        Location loc = s.to();
        Block below = loc.clone().subtract(0, 0.15, 0).getBlock();
        Block belowFeet = loc.clone().subtract(0, 0.35, 0).getBlock();
        boolean liquidBelow = below.isLiquid() || belowFeet.isLiquid()
                || below.getType().name().contains("LILY");
        boolean feetInLiquid = s.feetInLiquid;
        boolean headInLiquid = s.headInLiquid;
        boolean hasGround = CollisionUtil.isOnGround(loc.clone().add(0, -0.35, 0));

        boolean jesus = liquidBelow && !feetInLiquid && !headInLiquid && !hasGround
                && s.hSpeed > 0.16
                && s.deltaY >= -0.08 && s.deltaY <= 0.12
                && s.airTicks <= 2;

        if (jesus) {
            double buf = ctx.data().adjustBuffer("jesus", 1.0, 32.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                        0.5, 0.9,
                        "hSpeed=" + f(s.hSpeed) + " dY=" + f(s.deltaY)
                                + " below=" + below.getType().name().toLowerCase()
                                + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.NONE);
            }
        } else {
            decay(ctx);
        }
    }

    private void decay(CheckContext ctx) {
        ctx.data().adjustBuffer("jesus", -2.0, 32.0);
    }

    private static String f(double d) {
        return String.format("%.3f", d);
    }
}
