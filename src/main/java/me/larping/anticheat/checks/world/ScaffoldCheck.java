package me.larping.anticheat.checks.world;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Scaffold / fast-place / placement-reach check.
 *
 * <p>Fixes over the old implementation:
 * <ul>
 *   <li>Only real {@link org.bukkit.event.block.BlockPlaceEvent} placements are
 *       counted. The old version counted {@code PlayerInteractEvent} with
 *       right-click air too, so eating, shooting bows, throwing snowballs and
 *       opening doors all inflated the "placement" counter, double-counted
 *       with the actual block place.</li>
 *   <li>The placements-per-second cap reflects what is physically possible
 *       (around 12–14 blocks/s while bridging). The old effective limit of 35/s
 *       could never trigger.</li>
 *   <li>Adds a placement-reach component: blocks cannot be placed farther than
 *       the server interaction distance.</li>
 * </ul>
 */
public final class ScaffoldCheck extends WorldCheck {

    public ScaffoldCheck() {
        super("Scaffold");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        // Handled via evaluatePlace.
    }

    public void evaluatePlace(Player player, Block placed, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;

        CheckConfig cc = ctx.cfg().check("scaffold");
        double maxPerSec = cc.v1() > 0 ? cc.v1() : 14.0;
        double maxReach = cc.v2() > 0 ? cc.v2() : 6.0;
        double minConfirm = cc.v3() > 0 ? cc.v3() : 4.0;

        if (ctx.cfg().customModsEnabled() && ctx.cfg().customPlacementComp()) {
            maxPerSec += 4.0;
        }

        int perSec = ctx.data().placementsInLastMs(1000);
        if (perSec > maxPerSec) {
            double buf = ctx.data().adjustBuffer("scaffold", 1.0, 32.0);
            if (buf >= minConfirm) {
                flag(ctx, 0.5, 0.82,
                        "placements/sec=" + perSec + " max=" + (int) maxPerSec
                                + " buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("scaffold", -1.0, 32.0);
        }

        // Placement reach: distance from eye to the placed block face.
        if (placed != null) {
            Location eye = player.getEyeLocation();
            org.bukkit.World w = placed.getWorld();
            if (w != null && w.equals(eye.getWorld())) {
                Vector blockCenter = placed.getLocation().toCenterLocation().toVector();
                double dist = eye.toVector().distance(blockCenter);
                double limit = maxReach + 1.1; // from eye to far face of a block at reach limit
                if (ctx.cfg().compensatePing() && ctx.ping() > 120) {
                    limit += Math.min(0.6, (ctx.ping() - 120) / 250.0);
                }
                if (dist > limit) {
                    flag(ctx, 0.8, 0.95,
                            "placeReach=" + String.format("%.2f", dist)
                                    + " limit=" + String.format("%.2f", limit));
                }
            }
        }
    }
}
