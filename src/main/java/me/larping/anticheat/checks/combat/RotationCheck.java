package me.larping.anticheat.checks.combat;

import me.larping.anticheat.checks.Check;
import me.larping.anticheat.checks.CheckContext;
import org.bukkit.entity.Player;

public final class RotationCheck implements Check {

    @Override
    public String name() {
        return "Rotation";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void evaluate(Player player, CheckContext context) {
        if (!context.plugin().configManager().isCheckEnabled("rotation")) return;
        if (player.hasPermission(context.plugin().configManager().exemptPermission())) return;

        float pitch = player.getLocation().getPitch();
        float yaw = player.getLocation().getYaw();

        // Invalid pitch check (Vanilla pitch is between -90 and 90)
        if (pitch > 90.0f || pitch < -90.0f) {
            double vlAmount = 1.0;
            double confidence = 0.99;
            context.plugin().violations().add(
                    player,
                    "Rotation",
                    "Combat",
                    vlAmount,
                    confidence,
                    "invalidPitch=" + pitch
            );
        }
    }
}
