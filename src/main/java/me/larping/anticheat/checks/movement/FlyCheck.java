package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public final class FlyCheck extends MovementCheck {

    public FlyCheck() {
        super("Fly");
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void evaluate(Player player, CheckContext context) {
        if (shouldBypass(player, context)) return;
        if (!context.plugin().getConfig().getBoolean("checks.fly.enabled", true)) return;

        // Never flag Elytra gliding
        if (player.isGliding()) return;

        int airTicks = context.data().airTicks();
        int maxAirTicks = context.plugin().getConfig().getInt("checks.fly.air-ticks", 40);

        // Account for Jump Boost and Levitation / Slow Falling
        if (player.hasPotionEffect(PotionEffectType.JUMP_BOOST) ||
            player.hasPotionEffect(PotionEffectType.LEVITATION) ||
            player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            maxAirTicks += 25;
        }

        // Account for water, ladders, vines, scaffolding
        Block standingBlock = player.getLocation().getBlock();
        Block underBlock = player.getLocation().clone().subtract(0, 0.2, 0).getBlock();
        if (standingBlock.isLiquid() || underBlock.isLiquid() ||
            standingBlock.getType().name().contains("LADDER") ||
            standingBlock.getType().name().contains("VINE") ||
            standingBlock.getType().name().contains("SCAFFOLDING") ||
            underBlock.getType().name().contains("LADDER") ||
            underBlock.getType().name().contains("VINE")) {
            return;
        }

        // Custom mod compatibility compensation for custom flight/jumping/gliding mechanics
        if (context.plugin().getConfig().getBoolean("compatibility.custom-mods.enabled", true) &&
            context.plugin().getConfig().getBoolean("compatibility.custom-mods.movement-compensation", true)) {
            maxAirTicks += 30;
        }

        // Ping and TPS compensation
        if (context.ping() > 200 || context.tps() < 17.5) {
            maxAirTicks += 20;
        }

        double deltaY = context.data().deltaY();
        boolean hovering = Math.abs(deltaY) < 0.005 && !player.isOnGround();

        if (airTicks > maxAirTicks && hovering) {
            int buffer = context.data().flyBuffer(context.data().flyBuffer() + 1);
            int minConfirm = context.plugin().getConfig().getInt("checks.fly.min-confirmations", 6);
            if (buffer >= minConfirm) {
                double vlAmount = 0.4;
                double confidence = 0.85;
                context.plugin().violations().add(
                        player,
                        "Fly",
                        "Movement",
                        vlAmount,
                        confidence,
                        "airTicks=" + airTicks + ", deltaY=" + String.format("%.4f", deltaY) + ", buffer=" + buffer
                );
            }
        } else {
            context.data().flyBuffer(context.data().flyBuffer() - 1);
        }
    }
}
