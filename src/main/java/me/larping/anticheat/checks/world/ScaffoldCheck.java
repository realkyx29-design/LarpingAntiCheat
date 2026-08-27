package me.larping.anticheat.checks.world;

import me.larping.anticheat.checks.CheckContext;
import org.bukkit.entity.Player;

public final class ScaffoldCheck extends WorldCheck {

    public ScaffoldCheck() {
        super("Scaffold");
    }

    @Override
    public void evaluate(Player player, CheckContext context) {
        if (!context.plugin().getConfig().getBoolean("checks.scaffold.enabled", true)) return;
        if (player.hasPermission(context.plugin().getConfig().getString("exempt-permission", "lac.bypass"))) return;

        int placements = context.data().getRecentPlacementsPerSec();
        int maxPlacements = context.plugin().getConfig().getInt("checks.scaffold.max-placements-per-sec", 22);

        // Custom mod compatibility compensation for fast building / custom block placement mechanics
        if (context.plugin().getConfig().getBoolean("compatibility.custom-mods.enabled", true) &&
            context.plugin().getConfig().getBoolean("compatibility.custom-mods.block-placement-compensation", true)) {
            maxPlacements += 10;
        }

        if (placements > maxPlacements) {
            double vlAmount = 0.4;
            double confidence = 0.75;
            context.plugin().violations().add(
                    player,
                    "Scaffold",
                    "Interaction",
                    vlAmount,
                    confidence,
                    "placementsPerSec=" + placements + ", maxAllowed=" + maxPlacements
            );
        }
    }
}
