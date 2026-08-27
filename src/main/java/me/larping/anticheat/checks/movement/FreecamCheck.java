package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class FreecamCheck extends MovementCheck {

    public FreecamCheck() {
        super("Freecam");
    }

    @Override
    public void evaluate(Player player, CheckContext context) {
        if (shouldBypass(player, context)) return;
        if (!context.plugin().configManager().isCheckEnabled("freecam")) return;

        // If player is sneaking or stationary but sending rapid position changes over large distances without body rotation sync
        Location last = context.data().lastLocation();
        Location curr = player.getLocation();
        if (last == null) return;

        double dist = last.distance(curr);
        if (player.isSneaking() && dist > 10.0) {
            double vlAmount = 0.6;
            double confidence = 0.85;
            context.plugin().violations().add(
                    player,
                    "Freecam",
                    "Movement",
                    vlAmount,
                    confidence,
                    "sneakingDistance=" + String.format("%.2f", dist)
            );
        }
    }
}
