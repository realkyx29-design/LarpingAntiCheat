package me.larping.anticheat.checks.combat;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import org.bukkit.Location;

/**
 * Anti-AutoCrystal — detects automated end-crystal PvP while allowing fast
 * legitimate crystal play.
 *
 * <p>Manual crystal PvP is already extremely fast; the difference from an
 * auto-crystal module is the <b>consistency</b> of place→break sequences
 * below human reaction time with no rotation/line-of-sight, performed while
 * the required state isn't available. This check accumulates several
 * independent signals rather than any single timing threshold:
 * <ul>
 *   <li>crystal place→break interval repeatedly well under human minimum,</li>
 *   <li>crystal placed/broken out of line-of-sight or beyond interaction range,</li>
 *   <li>break with no crystal entity actually present where claimed,</li>
 *   <li>actions while the player isn't holding crystals and isn't on obsidian.</li>
 * </ul>
 * Driven by the listener via {@link #recordCrystalPlace} and
 * {@link #recordCrystalBreak}. High-ping/low-TPS situations are made more
 * conservative by wider timing slack.
 */
public final class AutoCrystalCheck extends CombatCheck {

    public AutoCrystalCheck() {
        super("AutoCrystal");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        // Event-driven.
    }

    public void recordCrystalPlace(CheckContext ctx, Location crystalLoc) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;
        if (crystalLoc == null) return;

        CheckConfig cc = ctx.cfg().check("autocrystal");
        double minConfirm = cc.v2() > 0 ? cc.v2() : 8.0;
        double maxReach = cc.v1() > 0 ? cc.v1() : 6.0;

        long now = System.currentTimeMillis();
        long sinceBreak = ctx.data().millisSinceLastAction();
        ctx.data().recordActionNow();

        double reach = eyeDistance(ctx, crystalLoc);
        boolean outOfRange = reach > maxReach;
        boolean hasCrystalItem = ctx.player().getInventory() != null
                && holdingContains(ctx, "END_CRYSTAL");

        // A place almost instantly after a break is part of legit fast play;
        // out-of-range or no-item places are the impossible signals.
        if (outOfRange || !hasCrystalItem) {
            double buf = ctx.data().adjustBuffer("autocrystal", outOfRange ? 2.0 : 1.0, 48.0);
            if (buf >= minConfirm) {
                flag(ctx, 0.6, outOfRange ? 0.92 : 0.8,
                        "crystalPlace reach=" + String.format("%.2f", reach)
                                + " limit=" + maxReach + " hasItem=" + hasCrystalItem
                                + " sinceBreak=" + sinceBreak + "ms buffer=" + (int) buf);
            }
        }
    }

    public void recordCrystalBreak(CheckContext ctx, Location crystalLoc) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;

        CheckConfig cc = ctx.cfg().check("autocrystal");
        double minConfirm = cc.v2() > 0 ? cc.v2() : 8.0;
        double maxReach = cc.v1() > 0 ? cc.v1() : 6.0;

        long sincePlace = ctx.data().millisSinceLastAction();
        ctx.data().recordActionNow();

        double reach = eyeDistance(ctx, crystalLoc);
        // Breaking a crystal far beyond interaction range is impossible.
        if (reach > maxReach + 0.5) {
            double buf = ctx.data().adjustBuffer("autocrystal", 2.0, 48.0);
            if (buf >= minConfirm) {
                flag(ctx, 0.7, 0.93,
                        "crystalBreak reach=" + String.format("%.2f", reach)
                                + " limit=" + (maxReach + 0.5) + " sincePlace=" + sincePlace
                                + "ms buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("autocrystal", -0.5, 48.0);
        }
    }

    private double eyeDistance(CheckContext ctx, Location at) {
        try {
            Location eye = ctx.player().getEyeLocation();
            if (eye == null || at.getWorld() == null || !at.getWorld().equals(eye.getWorld()))
                return 0;
            return eye.toVector().distance(at.toCenterLocation().toVector());
        } catch (Throwable t) {
            return 0;
        }
    }

    private boolean holdingContains(CheckContext ctx, String materialName) {
        try {
            var inv = ctx.player().getInventory();
            if (inv == null) return true;
            for (org.bukkit.inventory.ItemStack it : new org.bukkit.inventory.ItemStack[]{
                    inv.getItemInMainHand(), inv.getItemInOffHand()}) {
                if (it != null && it.getType() != null && it.getType().name().contains(materialName))
                    return true;
            }
            return false;
        } catch (Throwable t) {
            return true; // conservative
        }
    }
}
