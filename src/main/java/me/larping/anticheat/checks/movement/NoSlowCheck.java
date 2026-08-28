package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.physics.MovementSnapshot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * NoSlowdown detection.
 *
 * <p>Vanilla slows walking by ~40% while using an item with the use action
 * (food, potions, bow/crossbow, shield block, trident, spyglass, goat horn).
 * A NoSlow hack keeps full walk speed. We compare horizontal speed against the
 * slowed envelope derived from the player's real movement-speed attribute, with
 * slack for ice and active velocity. Only sustained over-limit movement flags
 * (confirmation buffer), and only when the player is genuinely using an item
 * and grounded — avoiding false positives during item-swap / quick taps.
 */
public final class NoSlowCheck extends MovementCheck {

    public NoSlowCheck() {
        super("NoSlow");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;
        MovementSnapshot s = ctx.move();
        if (s == null || s.gliding) return;

        CheckConfig cc = ctx.cfg().check("noslow");
        double minConfirm = cc.v1() > 0 ? cc.v1() : 4.0;

        boolean using = isUsingItem(ctx);
        if (!using || !s.serverGround) {
            ctx.data().adjustBuffer("noslow", -1.5, 32.0);
            return;
        }

        double hSpeed = s.hSpeed;
        // Slowed walk envelope from real base speed: base*1.3(sprint)*~1.5 ≈
        // slowed sprint-jump; then cap generously.
        // Slowed walk envelope from the real base speed (which already
        // includes sprint + speed-potion modifiers in Paper's attribute).
        double limit = s.baseSpeed * 1.3 * 1.5;
        limit = Math.max(limit, 0.145);
        if (s.onIce) limit *= 1.35;
        limit += s.velocityH;
        if (ctx.cfg().compensatePing() && ctx.ping() > 120)
            limit += Math.min(0.1, (ctx.ping() - 120) / 2000.0);

        boolean violation = hSpeed > limit && hSpeed > 0.16;

        if (violation) {
            double buf = ctx.data().adjustBuffer("noslow", 1.0, 32.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                        0.4, 0.85,
                        "hSpeed=" + f(hSpeed) + " limit=" + f(limit)
                                + " item=" + handItem(ctx).name().toLowerCase()
                                + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.NONE);
            }
        } else {
            ctx.data().adjustBuffer("noslow", -1.5, 32.0);
        }
    }

    private Material handItem(CheckContext ctx) {
        ItemStack hand = ctx.player().getInventory().getItemInMainHand();
        return hand != null ? hand.getType() : Material.AIR;
    }

    private boolean isUsingItem(CheckContext ctx) {
        if (!ctx.player().isHandRaised()) return false;
        Material m = handItem(ctx);
        return m.isEdible()
                || m == Material.BOW || m == Material.CROSSBOW || m == Material.TRIDENT
                || m == Material.SHIELD
                || m == Material.SPYGLASS || m == Material.GOAT_HORN
                || m == Material.WIND_CHARGE || m.name().endsWith("_POTION");
    }

    private static String f(double d) {
        return String.format("%.3f", d);
    }
}
