package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.util.CollisionUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Flight / glide / hover detection.
 *
 * <p>The vanilla game always applies gravity: a player airborne for longer
 * than the longest legitimate jump/glide must be accelerating downward.
 * The old check only flagged near-zero deltaY (perfect hover) and reset its
 * air counter on ANY downward movement, so horizontal flight bypassed it.
 *
 * <p>This version flags three independent signals:
 * <ol>
 *   <li>sustained airborne time without a downward arc (glide/fly)</li>
 *   <li>upward movement with no ground, no jump start and no potion (vertical fly)</li>
 *   <li>near-stationary hover high above ground</li>
 * </ol>
 * All with proper carve-outs for gliding, levitation, slow falling, climbing,
 * liquids, riptide, recent knockback/velocity, slime bounces and teleports.
 */
public final class FlyCheck extends MovementCheck {

    public FlyCheck() {
        super("Fly");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;

        CheckConfig cc = ctx.cfg().check("fly");

        // Legitimate flight-like states are fully exempt.
        if (player.isGliding()) return;
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) return;
        if (ctx.data().inRiptideGrace()) return;
        if (ctx.data().inTeleportGrace()) return;

        Location loc = player.getLocation();
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 0.8, 0).getBlock();
        Block below = loc.clone().subtract(0, 0.3, 0).getBlock();

        boolean inLiquid = feet.isLiquid() || head.isLiquid() || below.isLiquid();
        boolean climbing = player.isClimbing()
                || nameContains(feet, "LADDER", "VINE", "SCAFFOLDING", "TWISTING", "WEEPING", "CAVE_VINES");
        if (inLiquid || climbing) return;

        boolean serverGround = CollisionUtil.isOnGround(loc);
        int airTicks = ctx.data().airTicks();

        double deltaY = ctx.data().deltaY();
        double lastDeltaY = ctx.data().lastDeltaY();

        // ---- Potion-aware envelope ----------------------------------------
        int maxAirTicks = (int) cc.v1();                       // default 18
        if (player.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
            var fx = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
            maxAirTicks += 4 + (fx != null ? (fx.getAmplifier() + 1) * 3 : 3);
        }
        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            maxAirTicks += 30;
        }
        // Recent knockback / velocity: the server gave them air time.
        if (ctx.data().hasVelocity()) {
            maxAirTicks += 12;
        }
        // Slime block bounce: big upward launch.
        if (nameContains(below, "SLIME", "BED") || nameContains(feet, "SLIME")) {
            maxAirTicks += 14;
        }
        // SMP custom movement allowance.
        if (ctx.cfg().customModsEnabled() && ctx.cfg().customMovementComp()) {
            maxAirTicks += 6;
        }

        boolean violation;
        String reason;

        if (!serverGround && airTicks > maxAirTicks && deltaY >= -0.05) {
            // Still airborne way past any jump, and not (or barely) falling.
            violation = true;
            reason = "sustained-air airTicks=" + airTicks + " dY=" + String.format("%.3f", deltaY)
                    + " maxAir=" + maxAirTicks;
        } else if (!serverGround && airTicks > 2 && deltaY > 0.42 && lastDeltaY >= deltaY) {
            // Upward acceleration mid-air (gravity should slow the rise).
            // Jump Boost handled by the envelope; fireworks riptide exempt above.
            boolean boosted = player.hasPotionEffect(PotionEffectType.JUMP_BOOST)
                    || ctx.data().hasVelocity()
                    || nameContains(below, "SLIME", "HONEY");
            violation = !boosted;
            reason = "upward-accel dY=" + String.format("%.3f", deltaY)
                    + " lastDY=" + String.format("%.3f", lastDeltaY)
                    + " airTicks=" + airTicks;
        } else if (!serverGround && airTicks > 10 && Math.abs(ctx.data().horizontalSpeed()) < 0.02
                && Math.abs(deltaY) < 0.02) {
            // Classic hover: stationary in mid-air for half a second+.
            violation = true;
            reason = "hover airTicks=" + airTicks + " dY=" + String.format("%.3f", deltaY);
        } else {
            violation = false;
            reason = "";
        }

        if (violation) {
            double buf = ctx.data().adjustBuffer("fly", 1.0, 64.0);
            if (buf >= cc.v2()) {
                double confidence = 0.9;
                ctx.plugin().violations().flag(player, checkName, "Movement",
                        0.5, confidence, reason + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            }
        } else {
            ctx.data().adjustBuffer("fly", -2.0, 64.0);
        }
    }

    private static boolean nameContains(Block b, String... names) {
        String n = b.getType().name();
        for (String s : names) if (n.contains(s)) return true;
        return false;
    }
}
