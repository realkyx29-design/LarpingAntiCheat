package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class PhaseCheck extends MovementCheck {

    public PhaseCheck() {
        super("Phase");
    }

    @Override
    public void evaluate(Player player, CheckContext context) {
        if (shouldBypass(player, context)) return;
        if (!context.plugin().configManager().isCheckEnabled("phase")) return;

        Location to = player.getLocation();
        Block blockAtFeet = to.getBlock();
        Block blockAtHead = to.clone().add(0, 1, 0).getBlock();

        // Account for teleports, lag, ghost blocks, and stairs/slabs/fences/custom block colliders
        if (blockAtFeet.getType().isSolid() && blockAtHead.getType().isSolid()) {
            String feetName = blockAtFeet.getType().name();
            String headName = blockAtHead.getType().name();

            if (feetName.contains("STAIRS") || feetName.contains("SLAB") || feetName.contains("FENCE") ||
                feetName.contains("WALL") || feetName.contains("DOOR") || feetName.contains("GATE") ||
                feetName.contains("BED") || feetName.contains("CARPET") || feetName.contains("SCAFFOLDING") ||
                headName.contains("STAIRS") || headName.contains("SLAB") || headName.contains("FENCE") ||
                headName.contains("WALL") || headName.contains("DOOR") || headName.contains("GATE")) {
                return; // Completely exempt non-full blocks and custom colliders to prevent false positives!
            }

            double vlAmount = 0.5;
            double confidence = 0.78;
            context.plugin().violations().add(
                    player,
                    "Phase",
                    "Movement",
                    vlAmount,
                    confidence,
                    "solidFeet=" + feetName + ", solidHead=" + headName
            );
        }
    }
}
