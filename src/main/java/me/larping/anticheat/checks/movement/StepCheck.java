package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.util.CollisionUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Step / High-jump detection (single-move upward teleport onto terrain).
 *
 * <p>Vanilla auto-step rises 0.6 blocks per move at most, and a standing jump
 * leaves the ground (feet not on ground) before rising. A Step hack raises the
 * player ~1 block while staying "grounded"; tower/scaffold jumps are handled
 * by the Scaffold check. We flag a single upward delta beyond the step limit
 * while the feet remain essentially grounded, excluding jump boosts,
 * slime/beds, climbing, gliding, riptide and recent velocity.
 */
public final class StepCheck extends MovementCheck {

    public StepCheck() {
        super("Step");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;

        CheckConfig cc = ctx.cfg().check("step");
        double deltaY = ctx.data().deltaY();

        // Normal movement is downward/level most ticks — cheap exit.
        if (deltaY <= cc.v1()) {
            ctx.data().adjustBuffer("step", -2.0, 32.0);
            return;
        }

        if (player.isGliding() || ctx.data().inRiptideGrace() || ctx.data().hasVelocity()) {
            ctx.data().adjustBuffer("step", -2.0, 32.0);
            return;
        }

        Location loc = player.getLocation();
        Block feet = loc.getBlock();
        if (player.isClimbing() || feet.isLiquid()
                || feet.getType().name().contains("LADDER")
                || feet.getType().name().contains("VINE")
                || feet.getType().name().contains("SCAFFOLDING")) {
            ctx.data().adjustBuffer("step", -2.0, 32.0);
            return;
        }

        // Jump boost legitimately raises the arc — but a jump still leaves the
        // ground immediately, whereas a step stays at ground level.
        boolean wasAir = ctx.data().airTicks() > 1
                && !CollisionUtil.isOnGround(loc)
                && !CollisionUtil.isOnGround(loc.clone().subtract(0, 0.25, 0));

        Block below = loc.clone().subtract(0, 0.3, 0).getBlock();
        boolean bouncy = below.getType().name().contains("SLIME")
                || below.getType().name().contains("BED")
                || below.getType().name().contains("HONEY");

        boolean potionJump = player.hasPotionEffect(PotionEffectType.JUMP_BOOST)
                || player.hasPotionEffect(PotionEffectType.LEVITATION);

        // A real jump: player is airborne at the apex sample. A Step hack is a
        // grounded rise (feet stay within ~0.15 of a block surface).
        boolean groundedRise = !wasAir;
        boolean violation = groundedRise && !bouncy && !potionJump
                && deltaY > cc.v1();

        if (violation) {
            double buf = ctx.data().adjustBuffer("step", 1.0, 32.0);
            if (buf >= cc.v2()) {
                ctx.plugin().violations().flag(player, checkName, "Movement",
                        0.55, 0.9,
                        "dY=" + String.format("%.3f", deltaY)
                                + " hSpeed=" + String.format("%.3f", ctx.data().horizontalSpeed())
                                + " below=" + below.getType().name().toLowerCase()
                                + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            }
        } else {
            ctx.data().adjustBuffer("step", -2.0, 32.0);
        }
    }
}
