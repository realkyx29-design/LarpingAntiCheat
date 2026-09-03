package me.larping.anticheat.checks.honeypot;

import me.larping.anticheat.checks.Check;
import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.honeypot.DecoyService;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Anti-ESP / honey-pot detection (event-driven, no client inspection).
 *
 * <p>The honey-pot service invents "hidden bases" (invisible decoy markers /
 * packet fake-bases) that the server never reveals to a normal client. A
 * legitimate player cannot know they exist. This check flags players who:
 * <ul>
 *   <li>physically approach a hidden decoy (distance keeps shrinking while
 *       there is no line of sight), or</li>
 *   <li>attack / interact with the decoy entity.</li>
 * </ul>
 * Evidence accumulates across time and clears quickly on normal behaviour;
 * repeated reactions to <i>different</i> decoys (not a one-off coincidence)
 * are required, so coordinate sharing / prior exploration can't trigger it —
 * the decoy locations are freshly server-invented and never shown.
 */
public final class EspCheck implements Check {

    private final DecoyService decoys;

    public EspCheck(DecoyService decoys) {
        this.decoys = decoys;
    }

    @Override
    public String name() {
        return "ESP";
    }

    @Override
    public void evaluate(CheckContext ctx) {
        if (ctx.isFullyExempt() || !ctx.cfg().checkEnabled("esp")) return;
        Player player = ctx.player();
        try {
            double minConfirm = ctx.cfg().check("esp").v1() > 0
                    ? ctx.cfg().check("esp").v1() : 3.0;
            Location eye = player.getEyeLocation();
            for (DecoyService.Decoy decoy : decoys.decoysFor(player).values()) {
                Location l = decoy.location();
                if (l.getWorld() == null || !l.getWorld().equals(player.getWorld())) continue;
                double dist = eye.toVector().distance(l.toVector());
                if (dist > 100) continue;

                // Track sustained approach toward this specific decoy.
                boolean approaching = ctx.data().recordDecoyApproach(decoy.entityId(), dist);
                boolean reached = dist < 4.0;

                if (reached) {
                    // Physically arrived at a hidden decoy = strong evidence.
                    double buf = ctx.data().adjustBuffer("esp", 3.0, 40.0);
                    if (buf >= minConfirm) {
                        flag(player, ctx, "reachedHiddenBase dist=" + String.format("%.1f", dist)
                                + " buffer=" + (int) buf);
                    }
                } else if (approaching) {
                    double buf = ctx.data().adjustBuffer("esp", 0.5, 40.0);
                    if (buf >= minConfirm) {
                        flag(player, ctx, "pathingToHiddenBase dist=" + String.format("%.1f", dist)
                                + " buffer=" + (int) buf);
                    }
                } else {
                    ctx.data().adjustBuffer("esp", -1.0, 40.0);
                }
            }
        } catch (Throwable ignored) { }
    }

    /** Called when the player attacks or interacts with a decoy entity. */
    public void onDecoyInteraction(Player player, CheckContext ctx, String what) {
        if (ctx.isFullyExempt() || !ctx.cfg().checkEnabled("esp")) return;
        double buf = ctx.data().adjustBuffer("esp", 4.0, 40.0);
        double minConfirm = ctx.cfg().check("esp").v1() > 0 ? ctx.cfg().check("esp").v1() : 3.0;
        if (buf >= minConfirm) {
            flag(player, ctx, "interactedWithHiddenBase (" + what + ") buffer=" + (int) buf);
        }
    }

    private void flag(Player player, CheckContext ctx, String detail) {
        ctx.plugin().violations().flag(player, "ESP", "Honeypot",
                0.6, 0.9, detail,
                me.larping.anticheat.managers.ViolationManager.Setback.NONE);
    }
}
