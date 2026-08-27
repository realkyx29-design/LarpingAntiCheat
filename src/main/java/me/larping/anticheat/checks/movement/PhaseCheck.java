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
        Block blockAtHead = to.clone().add(0, 1, 0).getBlock();
        Block blockAtFeet = to.getBlock();

        // If player is inside solid non-passable blocks without being in water/webs/scaffolding
        if (blockAtFeet.getType().isSolid() && blockAtHead.getType().isSolid()) {
            String bName = blockAtFeet.getType().name();
            if (!bName.contains("DOOR") && !bName.contains("GATE") && !bName.contains("BED") && !bName.contains("CARPET")) {
                double vlAmount = 0.8;
                double confidence = 0.90;
                context.plugin().violations().add(
                        player,
                        "Phase",
                        "Movement",
                        vlAmount,
                        confidence,
                        "solidBlockFeet=" + blockAtFeet.getType() + ", solidBlockHead=" + blockAtHead.getType()
                );
            }
        }
    }
}
