package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.util.CollisionUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Spider / wall-climb detection.
 *
 * <p>Without a climbable block, a player cannot rise while touching only a
 * wall. We track sustained positive deltaY while a wall is adjacent to the
 * upper body, excluding: real climbable blocks (ladders/vines/scaffolding),
 * jump boost/levitation, gliding, riptide and recent knockback.
 */
public final class SpiderCheck extends MovementCheck {

    public SpiderCheck() {
        super("Spider");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;

        CheckConfig cc = ctx.cfg().check("spider");

        Location loc = player.getLocation();
        Block feet = loc.getBlock();
        String feetName = feet.getType().name();
        boolean climbable = player.isClimbing()
                || feetName.contains("LADDER") || feetName.contains("VINE")
                || feetName.contains("SCAFFOLDING") || feetName.contains("TWISTING")
                || feetName.contains("WEEPING") || feetName.contains("CAVE_VINES");

        boolean airborne = !CollisionUtil.isOnGround(loc);
        boolean rising = ctx.data().deltaY() > 0.09;
        boolean touchingWall = CollisionUtil.touchingWall(loc);
        double hSpeed = ctx.data().horizontalSpeed();

        boolean potionLift = player.hasPotionEffect(PotionEffectType.JUMP_BOOST)
                || player.hasPotionEffect(PotionEffectType.LEVITATION);

        boolean spider = airborne && rising && touchingWall && !climbable
                && !potionLift && !player.isGliding()
                && !ctx.data().inRiptideGrace()
                && !ctx.data().hasVelocity()
                && hSpeed < 0.45; // wall climb is slow vertical motion

        if (spider) {
            double buf = ctx.data().adjustBuffer("spider", 1.0, 64.0);
            int minTicks = (int) Math.max(cc.v1(), cc.v2());
            if (buf >= minTicks) {
                ctx.plugin().violations().flag(player, checkName, "Movement",
                        0.55, 0.92,
                        "climb dY=" + String.format("%.3f", ctx.data().deltaY())
                                + " hSpeed=" + String.format("%.3f", hSpeed)
                                + " airTicks=" + ctx.data().airTicks()
                                + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            }
        } else {
            ctx.data().adjustBuffer("spider", -2.0, 64.0);
        }
    }
}
