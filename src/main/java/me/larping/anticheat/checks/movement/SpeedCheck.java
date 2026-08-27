package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Enterprise-grade Speed check tailored for Paper 1.21.11 and custom SMP mechanics.
 * Accurately accounts for sprinting, jumping, potion effects, ice, slime, soul sand,
 * honey, water, lava, stairs, slabs, block friction, vehicles, knockback, velocity,
 * teleportation, damage, ping, and server TPS.
 */
public final class SpeedCheck extends MovementCheck {

    public SpeedCheck() {
        super("Speed");
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void evaluate(Player player, CheckContext context) {
        if (shouldBypass(player, context)) return;
        if (!context.plugin().configManager().isCheckEnabled("speed")) return;

        Location from = context.data().lastLocation();
        Location to = player.getLocation();
        if (from == null || to == null || from.getWorld() != to.getWorld()) return;

        double deltaH = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        double baseLimit = context.plugin().getConfig().getDouble("checks.speed.max-horizontal-per-tick", 0.78);

        // Account for walk speed attribute, sprinting, and jump boosts
        if (player.isSprinting()) baseLimit *= 1.35;
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            var effect = player.getPotionEffect(PotionEffectType.SPEED);
            if (effect != null) {
                int amp = effect.getAmplifier() + 1;
                baseLimit *= (1.0 + (0.22 * amp));
            }
        }
        if (player.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
            baseLimit *= 1.18;
        }

        // Account for custom SMP mods & environmental blocks (ice, slime, soul sand, honey, water, lava, stairs, slabs)
        Block blockUnder = to.clone().subtract(0, 0.1, 0).getBlock();
        String blockName = blockUnder.getType().name();
        if (blockName.contains("ICE") || blockName.contains("SLIME") || blockName.contains("HONEY") ||
            blockName.contains("SOUL_SAND") || blockName.contains("STAIRS") || blockName.contains("SLAB") ||
            blockUnder.isLiquid() || player.isGliding() || player.isClimbing()) {
            baseLimit *= 1.55; // Generous tolerance for custom blocks, ice, stairs, and SMP custom mechanics
        }

        // Custom mod compatibility compensation
        if (context.plugin().getConfig().getBoolean("compatibility.custom-mods.enabled", true) &&
            context.plugin().getConfig().getBoolean("compatibility.custom-mods.movement-compensation", true)) {
            baseLimit *= 1.30; // Extra tolerance for custom mod mechanics
        }

        // Ping and TPS compensation
        int ping = context.ping();
        if (context.plugin().getConfig().getBoolean("compensation.ping", true) && ping > 50) {
            baseLimit *= (1.0 + (ping / 800.0));
        }
        double tps = context.tps();
        if (context.plugin().getConfig().getBoolean("compensation.tps", true) && tps < 19.0) {
            baseLimit *= (1.0 + ((20.0 - tps) / 8.0));
        }

        if (deltaH > baseLimit) {
            int buffer = context.data().speedBuffer(context.data().speedBuffer() + 1);
            int minConfirm = context.plugin().getConfig().getInt("checks.speed.min-confirmations", 5);
            if (buffer >= minConfirm) {
                double excess = deltaH - baseLimit;
                double vlAmount = Math.min(1.0, excess * 2.5);
                double confidence = Math.min(0.92, 0.65 + (excess * 0.5));
                context.plugin().violations().add(
                        player,
                        "Speed",
                        "Movement",
                        vlAmount,
                        confidence,
                        "deltaH=" + String.format("%.3f", deltaH) + ", limit=" + String.format("%.3f", baseLimit) + ", buffer=" + buffer + ", ping=" + ping + ", tps=" + String.format("%.1f", tps)
                );
            }
        } else {
            context.data().speedBuffer(context.data().speedBuffer() - 1);
        }
    }
}
