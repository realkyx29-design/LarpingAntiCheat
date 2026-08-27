package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

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

        // Check config enable state
        if (!context.plugin().getConfig().getBoolean("checks.speed.enabled", true)) return;

        Location from = context.data().lastLocation();
        Location to = player.getLocation();
        if (from == null || to == null || from.getWorld() != to.getWorld()) return;

        double deltaH = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        double baseLimit = context.plugin().getConfig().getDouble("checks.speed.max-horizontal-per-tick", 0.78);

        // Account for walk speed attribute, sprinting, and jump boosts
        if (player.isSprinting()) baseLimit *= 1.32;
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amp = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            baseLimit *= (1.0 + (0.2 * amp));
        }
        if (player.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
            baseLimit *= 1.15;
        }

        // Account for custom SMP mods & environmental blocks (ice, slime, soul sand, honey, water, lava, stairs, slabs)
        Block blockUnder = to.clone().subtract(0, 0.1, 0).getBlock();
        String blockName = blockUnder.getType().name();
        if (blockName.contains("ICE") || blockName.contains("SLIME") || blockName.contains("HONEY") ||
            blockName.contains("SOUL_SAND") || blockName.contains("STAIRS") || blockName.contains("SLAB") ||
            blockUnder.isLiquid() || player.isGliding() || player.isClimbing()) {
            baseLimit *= 1.45; // Generous tolerance for custom blocks, ice, stairs, and SMP custom mechanics
        }

        // Custom mod compatibility compensation
        if (context.plugin().getConfig().getBoolean("compatibility.custom-mods.enabled", true) &&
            context.plugin().getConfig().getBoolean("compatibility.custom-mods.movement-compensation", true)) {
            baseLimit *= 1.25; // Extra tolerance for custom mod mechanics
        }

        // Ping and TPS compensation
        if (context.plugin().getConfig().getBoolean("compensation.ping", true) && context.ping() > 150) {
            baseLimit *= (1.0 + (context.ping() / 1000.0));
        }
        if (context.plugin().getConfig().getBoolean("compensation.tps", true) && context.tps() < 18.0) {
            baseLimit *= (1.0 + ((20.0 - context.tps()) / 10.0));
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
                        "deltaH=" + String.format("%.3f", deltaH) + ", limit=" + String.format("%.3f", baseLimit) + ", buffer=" + buffer
                );
            }
        } else {
            context.data().speedBuffer(context.data().speedBuffer() - 1);
        }
    }
}
