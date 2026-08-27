package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.util.CollisionUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Physics-based horizontal speed check.
 *
 * <p>Old design flaw: every compensation (sprint × ice × custom-mod × ping ×
 * TPS) was multiplied together, yielding a limit ~7× vanilla speed that even
 * blatant speed hacks never crossed. The new model starts from the real
 * vanilla sprint-jump cap, applies <b>additive</b> slack for latency/TPS and
 * one capped multiplier for speed potions/environment, and validates gliding
 * against elytra speeds.
 */
public final class SpeedCheck extends MovementCheck {

    public SpeedCheck() {
        super("Speed");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;

        // Gliding (elytra) has its own speed envelope.
        if (player.isGliding()) {
            evaluateGlide(player, ctx);
            return;
        }

        CheckConfig cc = ctx.cfg().check("speed");

        double speed = ctx.data().horizontalSpeed();
        // Ignore duplicate / look-only packets.
        if (speed < 1.0e-4) return;

        // --- Vanilla physics base limit (blocks/tick) ----------------------
        // Sprint-jumping on flat ground peaks around 0.358 b/t. Use that as
        // the ceiling for ground movement; airborne (jumping between blocks)
        // gets slightly more headroom.
        double limit = cc.v1();
        boolean onGround = CollisionUtil.isOnGround(player.getLocation());
        if (!onGround) limit += 0.08;               // jump arc travel
        if (player.isSprinting()) limit += 0.015;

        // --- Speed potion: real multiplicative effect ----------------------
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            var fx = player.getPotionEffect(PotionEffectType.SPEED);
            if (fx != null) limit *= 1.0 + 0.20 * (fx.getAmplifier() + 1);
        }

        // --- Environmental friction bonuses (real physics, capped) ---------
        limit *= environmentMultiplier(player);

        // --- Active knockback / velocity: the server knows the vector ------
        limit += ctx.data().expectedVelocityHorizontal();

        // --- Liquid / climbing: vanilla caps movement far below run speed.
        // Use a tight ceiling so water-walking speedhacks still register. ---
        Location loc = player.getLocation();
        Block feet = loc.getBlock();
        boolean feetLiquid = feet.isLiquid();
        boolean headLiquid = loc.clone().add(0, 0.9, 0).getBlock().isLiquid();
        boolean inWeb = feet.getType().name().contains("COBWEB");
        if (inWeb) limit = Math.min(limit, 0.10);
        else if (feetLiquid || headLiquid) limit = Math.min(limit, headLiquid ? 0.13 : 0.20);

        // --- Riptide launch spike ------------------------------------------
        if (ctx.data().inRiptideGrace()) {
            bufferedFlag(ctx, false, 0, 0, "", cc.v2());
            return;
        }

        // --- Latency / TPS slack: additive, bounded, never multiplicative ---
        var cfg = ctx.cfg();
        if (cfg.compensatePing() && ctx.ping() > 80) {
            // Lag can coalesce two ticks of movement into one packet.
            limit += Math.min(0.18, (ctx.ping() - 80) / 1500.0);
        }
        if (cfg.compensateTps() && ctx.tps() < 19.0) {
            limit += Math.min(0.35, (20.0 - ctx.tps()) * 0.06);
        }
        if (cfg.customModsEnabled() && cfg.customMovementComp()) {
            limit *= 1.10; // small, bounded allowance for SMP abilities
        }

        boolean violation = speed > limit;
        double excess = speed - limit;
        if (violation) {
            double confidence = Math.min(0.97, 0.62 + excess * 1.6);
            bufferedFlag(ctx, true, Math.min(1.0, 0.35 + excess * 2.2), confidence,
                    "hSpeed=" + fmt(speed) + " limit=" + fmt(limit)
                            + " onGround=" + onGround + " ping=" + ctx.ping()
                            + " tps=" + String.format("%.1f", ctx.tps()),
                    cc.v2());
        } else {
            bufferedFlag(ctx, false, 0, 0, "", cc.v2());
        }
    }

    private void evaluateGlide(Player player, CheckContext ctx) {
        CheckConfig cc = ctx.cfg().check("speed");
        double speed = ctx.data().horizontalSpeed();
        double limit = 1.9; // sustained elytra ~1.6-1.9 b/t

        if (ctx.data().hasGlideFireworkBoost()) limit = 2.6;
        if (ctx.data().inRiptideGrace()) limit = 3.2;

        var cfg = ctx.cfg();
        if (cfg.compensatePing() && ctx.ping() > 80) limit += Math.min(0.3, (ctx.ping() - 80) / 900.0);
        if (cfg.compensateTps() && ctx.tps() < 19.0) limit += Math.min(0.4, (20.0 - ctx.tps()) * 0.08);

        boolean violation = speed > limit;
        if (violation) {
            double excess = speed - limit;
            double confidence = Math.min(0.97, 0.70 + excess * 0.9);
            // Glide flags use the same buffer key so confirmations accumulate.
            double buf = ctx.data().adjustBuffer("speed", 1.0, 64.0);
            if (buf >= cc.v2()) {
                ctx.plugin().violations().flag(player, checkName, "Movement",
                        Math.min(1.0, 0.4 + excess), confidence,
                        "elytraH=" + fmt(speed) + " limit=" + fmt(limit)
                                + " firework=" + ctx.data().hasGlideFireworkBoost(),
                        me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            }
        } else {
            ctx.data().adjustBuffer("speed", -1.5, 64.0);
        }
    }

    /**
     * Real environmental speed factors. Ice gives a friction bonus; slime
     * blocks bounce. Water/cobweb slow the player and are handled separately
     * via tight ceilings above, so they get no bonus multiplier here.
     */
    private double environmentMultiplier(Player player) {
        Location loc = player.getLocation();
        Block below = loc.clone().subtract(0, 0.2, 0).getBlock();
        String name = below.getType().name();
        double mult = 1.0;
        if (name.contains("ICE")) mult = 1.22;
        else if (name.contains("SLIME")) mult = 1.15;
        // Honey / soul sand / liquids slow you down (no bonus). Climbing
        // ladders/vines is also slow; that's bounded separately.
        return mult;
    }

    private static String fmt(double d) {
        return String.format("%.3f", d);
    }
}
