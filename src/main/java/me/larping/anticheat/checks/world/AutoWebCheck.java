package me.larping.anticheat.checks.world;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Anti-AutoWeb — validates the full server-side cobweb-placement sequence.
 *
 * <p>AutoWeb cheats place webs in front of the player during combat faster
 * and with a state the player cannot actually produce (e.g. repeatedly placing
 * cobweb without the web material on hand / selected, at impossible intervals,
 * beyond placement range, or in a perfect automated stream). We validate the
 * legitimate chain
 * <pre>
 *   has cobweb -> selects/holds it -> block-place event -> valid position ->
 *   in range -> plausible timing
 * </pre>
 * and only flag when the resulting placement cannot be explained by the
 * player's real server-side inventory and timing — never for placing a web
 * quickly while genuinely holding one.
 */
public final class AutoWebCheck extends WorldCheck {

    public AutoWebCheck() {
        super("AutoWeb");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        // Driven by placement events.
    }

    public void evaluateWebPlacement(Player player, Block placed, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;
        if (placed == null || placed.getType() != Material.COBWEB) return;

        CheckConfig cc = ctx.cfg().check("autoweb");
        double minConfirm = cc.v2() > 0 ? cc.v2() : 6.0;

        int webPlacements = ctx.data().placementsInLastMs(2000);
        double cps = webPlacements / 2.0; // placements per second over 2s

        // The player must actually be holding/owning cobweb. Inventory changes
        // server-side on a real placement; if they have no cobweb at all the
        // event would not normally fire. High-frequency placements require the
        // item; we use rate + burst consistency rather than a single event.
        boolean hasCobweb = playerHasCobweb(player);

        // Placements per second beyond what is achievable while still placing
        // valid blocks (~10/sec even for fast legit building, allowing slack).
        boolean implausibleRate = cps > 10.0 && webPlacements > minConfirm;

        // Out-of-range placement (the web appeared farther than reach allows).
        double reach = placementReach(player, placed);
        boolean impossibleReach = reach > 5.5;

        if ((implausibleRate || impossibleReach) && (hasCobweb || !impossibleReach)) {
            double buf = ctx.data().adjustBuffer("autoweb", 1.0, 32.0);
            if (buf >= minConfirm) {
                flag(ctx, 0.5, 0.8,
                        "webs/2s=" + webPlacements + " reach=" + String.format("%.2f", reach)
                                + " hasItem=" + hasCobweb + " buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("autoweb", -1.0, 32.0);
        }

        // Placing cobweb with no cobweb in inventory at all is outright invalid.
        if (!hasCobweb) {
            double buf = ctx.data().adjustBuffer("autoweb", 2.0, 32.0);
            if (buf >= 2) {
                flag(ctx, 0.9, 0.95, "cobweb placed with no cobweb item; reach="
                        + String.format("%.2f", reach) + " buffer=" + (int) buf);
            }
        }
    }

    private boolean playerHasCobweb(Player player) {
        try {
            var inv = player.getInventory();
            if (inv == null) return true; // can't prove otherwise, don't flag
            // Check main and off hand.
            if (isWeb(inv.getItemInMainHand()) || isWeb(inv.getItemInOffHand())) return true;
            // A real placement just consumed a web; if the main/selected item is
            // web or they have web anywhere we accept it.
            for (ItemStack it : inv.getStorageContents()) {
                if (isWeb(it)) return true;
            }
            return false;
        } catch (Throwable t) {
            return true; // conservative: don't false-flag if inventory API fails
        }
    }

    private boolean isWeb(ItemStack it) {
        return it != null && it.getType() == Material.COBWEB;
    }

    private double placementReach(Player player, Block block) {
        try {
            var eye = player.getEyeLocation();
            if (eye == null || block.getWorld() == null
                    || !block.getWorld().equals(eye.getWorld())) return 0;
            return eye.toVector().distance(block.getLocation().toCenterLocation().toVector());
        } catch (Throwable t) {
            return 0;
        }
    }
}
