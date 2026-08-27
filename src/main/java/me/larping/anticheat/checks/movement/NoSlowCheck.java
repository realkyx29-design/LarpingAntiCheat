package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.util.CollisionUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * NoSlowdown detection.
 *
 * <p>Vanilla imposes ~40% slowdown while using items that require the use
 * action (food, potions, bow/crossbow, shield block, trident, spyglass, goat
 * horn). A NoSlow hack keeps full walk speed. We compare horizontal speed
 * against the slowed envelope while the player is genuinely in the use state,
 * with carve-outs for sprint-jump slack, ice and velocity.
 */
public final class NoSlowCheck extends MovementCheck {

    public NoSlowCheck() {
        super("NoSlow");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;
        if (player.isGliding()) return;

        CheckConfig cc = ctx.cfg().check("noslow");
        double minConfirm = cc.v1() > 0 ? cc.v1() : 4.0;

        boolean using = isUsingItem(player);
        if (!using) {
            ctx.data().adjustBuffer("noslow", -2.0, 32.0);
            return;
        }

        double hSpeed = ctx.data().horizontalSpeed();
        boolean onGround = CollisionUtil.isOnGround(player.getLocation());

        // Slowed walk speed is ~0.10 grounded; allow sprint-jump slack + ice.
        double limit = onGround ? 0.145 : 0.19;
        Material below = player.getLocation().clone().subtract(0, 0.2, 0).getBlock().getType();
        if (below.name().contains("ICE")) limit *= 1.35;
        limit += ctx.data().expectedVelocityHorizontal();
        if (ctx.cfg().compensatePing() && ctx.ping() > 120) {
            limit += Math.min(0.1, (ctx.ping() - 120) / 2000.0);
        }

        // The slowdown doesn't apply while airborne jumping straight up briefly;
        // require the player to be sustained moving (not a tiny twitch).
        boolean violation = hSpeed > limit && hSpeed > 0.16;

        if (violation) {
            double buf = ctx.data().adjustBuffer("noslow", 1.0, 32.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(player, checkName, "Movement",
                        0.4, 0.85,
                        "hSpeed=" + String.format("%.3f", hSpeed) + " limit=" + String.format("%.3f", limit)
                                + " item=" + (player.getInventory().getItemInMainHand().getType())
                                + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.NONE);
            }
        } else {
            ctx.data().adjustBuffer("noslow", -1.5, 32.0);
        }
    }

    private boolean isUsingItem(Player player) {
        if (player.isHandRaised()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            Material m = hand.getType();
            // Vanilla slows the walk speed for these use actions.
            return m.isEdible()
                    || m == Material.BOW || m == Material.CROSSBOW || m == Material.TRIDENT
                    || m == Material.SHIELD
                    || m == Material.SPYGLASS || m == Material.GOAT_HORN
                    || m == Material.WIND_CHARGE || m.name().endsWith("_POTION");
        }
        return false;
    }
}
