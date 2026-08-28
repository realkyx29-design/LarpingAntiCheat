package me.larping.anticheat.checks.world;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.data.PlayerData;
import me.larping.anticheat.modifiers.Capabilities;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Nuker / block-reach detection that understands legitimate area-mining.
 *
 * <p>Plain survival play breaks at most a handful of blocks per second and
 * only within interaction range. A nuker cheat breaks a large volume and/or
 * blocks far out of reach. But <b>custom pickaxes with 3x3 / 4x4 area abilities
 * legitimately break many blocks at once</b>, so this check:
 * <ul>
 *   <li>raises the per-second allowance when the held tool is a recognised
 *       area miner, scaled by its area radius ({@link Capabilities#areaMineRadius});</li>
 *   <li>only flags reach violations for blocks genuinely beyond a generous
 *       interaction distance (the central block of an area-mine is always in
 *       range; neighbours stay within the radius).</li>
 * </ul>
 * Custom enchants from your plugin are also picked up via the registered
 * {@code CustomModifierProvider}, so real abilities are never called nuker.
 */
public final class NukerCheck extends WorldCheck {

    public NukerCheck() {
        super("Nuker");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        // Driven by break events.
    }

    public void evaluateBreak(Player player, BlockBreakEvent event, Capabilities caps, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;

        Block block = event.getBlock();
        CheckConfig cc = ctx.cfg().check("nuker");
        double basePerSec = cc.v1() > 0 ? cc.v1() : 14.0;
        double maxReach = cc.v2() > 0 ? cc.v2() : 6.0;

        boolean area = caps != null && caps.areaMining;
        int radius = caps != null ? caps.areaMineRadius : 0;

        // ---- Rate check, adjusted for legitimate area mining ----
        // An area ability can legitimately produce up to (2r+1)^2-ish blocks
        // per swing, several swings per second. Scale the allowance so the
        // real ability is always under it while true nuker volume is not.
        double effectivePerSec;
        if (area && radius > 0) {
            int areaCells = (2 * radius + 1) * (2 * radius + 1);
            // a few full-area swings per second, plus generous headroom.
            effectivePerSec = basePerSec + areaCells * 6.0;
        } else {
            effectivePerSec = basePerSec;
        }

        int rate = ctx.data().breaksInLastSecond();
        if (rate > effectivePerSec) {
            double buf = ctx.data().adjustBuffer("nuker", 1.0, 32.0);
            if (buf >= 2) {
                flag(ctx, 0.7, 0.9,
                        "breaks/sec=" + rate + " allowed=" + (int) effectivePerSec
                                + (area ? " (area-miner r" + radius + ")" : "")
                                + " buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("nuker", -1.0, 32.0);
        }

        // ---- Reach check: never affected by area mining ----
        Location eye = player.getEyeLocation();
        if (block.getWorld() != null && eye.getWorld() != null
                && block.getWorld().equals(eye.getWorld())) {
            double dist = eye.toVector().distance(block.getLocation().toCenterLocation().toVector());
            // Survival interaction range ~4.5; area neighbours add a little;
            // use a generous ceiling that still catches impossible reach.
            double limit = maxReach + (area ? radius : 0) + 0.8;
            if (dist > limit) {
                flag(ctx, 0.9, 0.97,
                        "breakReach=" + String.format("%.2f", dist)
                                + " limit=" + String.format("%.2f", limit)
                                + " block=" + block.getType().name().toLowerCase());
            }
        }
    }
}
