package me.larping.anticheat.checks.world;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Nuker / block-reach detection.
 *
 * <p>Survival players can break ~6-9 blocks per second at most (instant-mine
 * soft blocks with efficiency tools), and only blocks within interaction
 * range. A nuker cheat breaks many blocks per second and/or blocks beyond
 * reach. Creative mode breaks instantly by design, so rate detection is
 * skipped there; reach detection still applies.
 */
public final class NukerCheck extends WorldCheck {

    public NukerCheck() {
        super("Nuker");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        // Driven by break events.
    }

    public void evaluateBreak(Player player, Block block, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;

        CheckConfig cc = ctx.cfg().check("nuker");
        double maxPerSec = cc.v1() > 0 ? cc.v1() : 14.0;
        double maxReach = cc.v2() > 0 ? cc.v2() : 6.0;

        // Rate: creative can legally instamine, survival cannot.
        if (player.getGameMode() != GameMode.CREATIVE) {
            int rate = ctx.data().breaksInLastSecond();
            if (rate > maxPerSec) {
                double buf = ctx.data().adjustBuffer("nuker", 1.0, 32.0);
                if (buf >= 2) {
                    flag(ctx, 0.7, 0.9,
                            "breaks/sec=" + rate + " max=" + (int) maxPerSec
                                    + " buffer=" + (int) buf);
                }
            } else {
                ctx.data().adjustBuffer("nuker", -1.0, 32.0);
            }
        }

        // Reach: eye to block center. Interaction range in survival is ~4.5,
        // creative ~5-6; we use a generous ceiling regardless of gamemode.
        Location eye = player.getEyeLocation();
        if (block.getWorld() != null && block.getWorld().equals(eye.getWorld())) {
            double dist = eye.toVector().distance(block.getLocation().toCenterLocation().toVector());
            double limit = maxReach + 0.6;
            if (dist > limit) {
                flag(ctx, 0.9, 0.97,
                        "breakReach=" + String.format("%.2f", dist)
                                + " limit=" + String.format("%.2f", limit)
                                + " block=" + block.getType().name().toLowerCase());
            }
        }
    }
}
