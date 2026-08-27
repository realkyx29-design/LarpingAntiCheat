package me.larping.anticheat.checks.combat;

import me.larping.anticheat.checks.CheckContext;
import org.bukkit.entity.Player;
import java.util.List;

public final class AutoClickerCheck extends CombatCheck {

    public AutoClickerCheck() {
        super("AutoClicker");
    }

    @Override
    public void evaluate(Player player, CheckContext context) {
        if (!context.plugin().getConfig().getBoolean("checks.autoclicker.enabled", true)) return;
        if (player.hasPermission(context.plugin().getConfig().getString("exempt-permission", "lac.bypass"))) return;

        int cps = context.data().getRecentCPS();
        int maxCps = context.plugin().getConfig().getInt("checks.autoclicker.max-cps", 20);

        // Custom mod compatibility compensation for click items/abilities
        if (context.plugin().getConfig().getBoolean("compatibility.custom-mods.enabled", true) &&
            context.plugin().getConfig().getBoolean("compatibility.custom-mods.combat-compensation", true)) {
            maxCps += 5;
        }

        if (cps > maxCps) {
            // Analyze click interval consistency (autoclickers often have extremely invariant millisecond intervals)
            List<Long> timestamps = context.data().getClickTimestamps();
            if (timestamps.size() >= 10) {
                long totalDiff = 0;
                long minDiff = Long.MAX_VALUE;
                long maxDiff = 0;
                for (int i = 1; i < timestamps.size(); i++) {
                    long diff = timestamps.get(i) - timestamps.get(i - 1);
                    totalDiff += diff;
                    minDiff = Math.min(minDiff, diff);
                    maxDiff = Math.max(maxDiff, diff);
                }
                double avgDiff = (double) totalDiff / (timestamps.size() - 1);
                long variance = maxDiff - minDiff;

                // Suspiciously consistent click intervals (low variance at high CPS indicates macro / autoclicker)
                if (variance < 8 && avgDiff < 55) {
                    double vlAmount = 0.5;
                    double confidence = 0.82;
                    context.plugin().violations().add(
                            player,
                            "AutoClicker",
                            "Combat",
                            vlAmount,
                            confidence,
                            "cps=" + cps + ", avgInterval=" + String.format("%.1f", avgDiff) + "ms, variance=" + variance + "ms"
                    );
                }
            }
        }
    }
}
