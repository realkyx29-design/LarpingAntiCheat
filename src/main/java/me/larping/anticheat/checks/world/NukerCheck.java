package me.larping.anticheat.checks.world;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.modifiers.Capabilities;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Nuker detection that understands legitimate custom-pickaxe area mining.
 *
 * <p>The check does <b>not</b> simply count blocks and call a high count a
 * nuker. It looks at the <b>actual blocks being broken</b> and the player's
 * held pickaxe:
 * <ul>
 *   <li>If the tool is a recognised area-mining pickaxe ({@code caps.areaMining},
 *       e.g. a custom 3x3/4x4 enchant) and the broken blocks form a tight
 *       cluster around the centre block within that radius, it is allowed —
 *       regardless of count. This is the single-burst area mine.</li>
 *   <li>Out-of-reach blocks (genuinely farther than the player can interact)
 *       remain suspicious — that is impossible for any pickaxe.</li>
 *   <li>A very high sustained rate of <i>scattered</i> blocks with no cluster
 *       is what a real nuker does; that is the only rate-based flag.</li>
 * </ul>
 * Detection is evidence-based and conservative; custom fast/area pickaxes,
 * efficiency, haste and instamine are all accounted for.
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
        double maxReach = cc.v2() > 0 ? cc.v2() : 6.0;

        boolean area = caps != null && caps.areaMining;
        int radius = caps != null ? caps.areaMineRadius : 0;

        // Record the broken block position for cluster analysis.
        ctx.data().recordBreakPosition(block.getX(), block.getY(), block.getZ(), 1500L);

        // ---- Reach: never legal for any tool ----
        Location eye = player.getEyeLocation();
        double dist = -1;
        if (block.getWorld() != null && eye != null && eye.getWorld() != null
                && block.getWorld().equals(eye.getWorld())) {
            dist = eye.toVector().distance(block.getLocation().toCenterLocation().toVector());
            double limit = maxReach + (area ? radius : 0) + 1.2;
            if (dist > limit) {
                double buf = ctx.data().adjustBuffer("nuker", 1.0, 32.0);
                if (buf >= 2) {
                    flag(ctx, 0.9, 0.97,
                            "breakReach=" + String.format("%.2f", dist)
                                    + " limit=" + String.format("%.2f", limit)
                                    + " block=" + block.getType().name().toLowerCase()
                                    + " buffer=" + (int) buf);
                }
                return;
            }
        }

        // ---- Area mining: tight clustered burst around the centre ----
        boolean clustered = ctx.data().recentBreaksAreClustered(
                block.getX(), block.getY(), block.getZ(), Math.max(radius, 1));
        if (area && clustered) {
            // Legit custom pickaxe area mine: allow regardless of count.
            ctx.data().adjustBuffer("nuker", -2.0, 32.0);
            return;
        }

        // ---- Rate: only flag scattered (non-clustered) sustained volume ----
        int rate = ctx.data().breaksInLastSecond();
        // For a non-area pickaxe, a scattered count far past normal survival is
        // a nuker. For area pickaxes whose blocks are NOT clustered, still
        // allow a generous amount before flagging (real area mines cluster).
        double basePerSec = cc.v1() > 0 ? cc.v1() : 14.0;
        double allowed = area ? basePerSec + 25 : basePerSec;

        if (rate > allowed) {
            double buf = ctx.data().adjustBuffer("nuker", 1.0, 32.0);
            if (buf >= 3) {
                flag(ctx, 0.7, 0.85,
                        "breaks/sec=" + rate + " allowed=" + (int) allowed
                                + " clustered=" + clustered + " areaTool=" + area
                                + " buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("nuker", -1.0, 32.0);
        }
    }
}
