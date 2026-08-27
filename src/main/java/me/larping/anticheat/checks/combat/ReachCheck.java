package me.larping.anticheat.checks.combat;

import me.larping.anticheat.checks.CheckContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class ReachCheck extends CombatCheck {

    public ReachCheck() {
        super("Reach");
    }

    @Override
    public void evaluate(Player player, CheckContext context) {
        // Handled via evaluateAttack
    }

    public void evaluateAttack(Player attacker, LivingEntity target, CheckContext context) {
        if (!context.plugin().getConfig().getBoolean("checks.reach.enabled", true)) return;
        if (attacker.hasPermission(context.plugin().getConfig().getString("exempt-permission", "lac.bypass"))) return;
        if (attacker.getGameMode() == org.bukkit.GameMode.SPECTATOR || attacker.isInsideVehicle()) return;

        double distance = attacker.getLocation().distance(target.getLocation());
        double maxDistance = context.plugin().getConfig().getDouble("checks.reach.max-distance", 3.65);

        // Account for ping, latency, hitboxes, and server/client timing differences
        int ping = attacker.getPing();
        if (ping > 50) {
            maxDistance += (ping / 300.0); // Allow extra leeway for higher ping
        }

        // Custom mod compatibility compensation for custom weapons or reach abilities
        if (context.plugin().getConfig().getBoolean("compatibility.custom-mods.enabled", true) &&
            context.plugin().getConfig().getBoolean("compatibility.custom-mods.combat-compensation", true)) {
            maxDistance += 0.5;
        }

        if (distance > maxDistance) {
            double excess = distance - maxDistance;
            double vlAmount = Math.min(1.0, excess * 1.8);
            double confidence = Math.min(0.90, 0.70 + (excess * 0.3));
            context.plugin().violations().add(
                    attacker,
                    "Reach",
                    "Combat",
                    vlAmount,
                    confidence,
                    "distance=" + String.format("%.2f", distance) + ", maxAllowed=" + String.format("%.2f", maxDistance) + ", ping=" + ping
            );
        }
    }
}
