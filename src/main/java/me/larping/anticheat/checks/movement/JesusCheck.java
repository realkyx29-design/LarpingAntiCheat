package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.util.CollisionUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Jesus / water-walk detection.
 *
 * <p>Vanilla: when walking over a liquid you either fall in (feet inside the
 * liquid, horizontal speed dips to ~0.11 b/t) or you are swimming/standing on
 * a solid block under the surface. A Jesus hack keeps the player at the
 * surface with near-ground horizontal speed while no collision exists beneath.
 *
 * <p>We require the player to be moving, the block below to be liquid, the
 * player's feet NOT inside the liquid (standing on top), no real ground
 * collision within 0.35 blocks, and no diving/swimming motion — sustained.
 */
public final class JesusCheck extends MovementCheck {

    public JesusCheck() {
        super("Jesus");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;

        CheckConfig cc = ctx.cfg().check("jesus");
        Location loc = player.getLocation();

        Block feet = loc.getBlock();
        Block below = loc.clone().subtract(0, 0.15, 0).getBlock();
        Block above = loc.clone().add(0, 1.0, 0).getBlock();

        boolean belowLiquid = below.isLiquid();
        boolean feetInLiquid = feet.isLiquid();
        boolean headInLiquid = above.isLiquid();

        double hSpeed = ctx.data().horizontalSpeed();
        double deltaY = ctx.data().deltaY();
        boolean hasGround = CollisionUtil.isOnGround(loc.clone().add(0, -0.35, 0));

        // Riptide over water is exempt; boats handled by vehicle exemption.
        if (ctx.data().inRiptideGrace() || player.isGliding()) {
            ctx.data().adjustBuffer("jesus", -3.0, 32.0);
            return;
        }

        // Riding on top of a solid block covered by a thin layer of water is
        // legitimate: hasGround catches it.
        boolean jesus = belowLiquid && !feetInLiquid && !headInLiquid
                && !hasGround
                && hSpeed > 0.16
                && deltaY >= -0.08 && deltaY <= 0.12
                && ctx.data().airTicks() <= 2;

        if (jesus) {
            double buf = ctx.data().adjustBuffer("jesus", 1.0, 32.0);
            if (buf >= cc.v1()) {
                ctx.plugin().violations().flag(player, checkName, "Movement",
                        0.5, 0.9, "hSpeed=" + String.format("%.3f", hSpeed)
                                + " dY=" + String.format("%.3f", deltaY)
                                + " below=" + below.getType().name().toLowerCase()
                                + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            }
        } else {
            ctx.data().adjustBuffer("jesus", -2.0, 32.0);
        }
    }
}
