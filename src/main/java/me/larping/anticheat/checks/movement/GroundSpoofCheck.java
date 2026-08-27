package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import org.bukkit.entity.Player;

public final class GroundSpoofCheck extends MovementCheck {

    public GroundSpoofCheck() {
        super("GroundSpoof");
    }

    @Override
    public void evaluate(Player player, CheckContext context) {
        if (shouldBypass(player, context)) return;
        if (!context.plugin().configManager().isCheckEnabled("groundspoof")) return;

        boolean clientOnGround = player.isOnGround();
        int airTicks = context.data().airTicks();
        double deltaY = context.data().deltaY();

        // If client claims onGround while falling fast in mid-air (airTicks > 10 and deltaY < -0.5)
        if (clientOnGround && airTicks > 10 && deltaY < -0.5) {
            double vlAmount = 0.5;
            double confidence = 0.88;
            context.plugin().violations().add(
                    player,
                    "GroundSpoof",
                    "Movement",
                    vlAmount,
                    confidence,
                    "airTicks=" + airTicks + ", deltaY=" + String.format("%.3f", deltaY) + ", clientOnGround=" + clientOnGround
            );
        }
    }
}
