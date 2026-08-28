package me.larping.anticheat.checks.combat;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Anti-AutoTotem — detects automated totem-swap behaviour without flagging a
 * fast legitimate player.
 *
 * <p>A vanilla totem saves the player only when it is actually in their main
 * or off hand at the moment of lethal damage. AutoTotem cheats repeatedly and
 * near-instantly place a totem into the off hand in the tiny window between
 * lethal hits, faster and more consistent than human reaction, then swap it
 * back. This check accumulates evidence across events (driven by the listener
 * via {@link #recordTotemUse}):
 * <ul>
 *   <li>many totem uses in a short window with no sustained off-hand totem,</li>
 *   <li>an off-hand totem swap occurring within an implausible reaction
 *       interval (well below human minimum, accounting for ping/TPS),</li>
 *   <li>repeated perfectly-timed emergency re-swap cycles.</li>
 * </ul>
 * A single fast swap never flags — only repeated, sub-human, consistent
 * automated patterns do.
 */
public final class AutoTotemCheck extends CombatCheck {

    public AutoTotemCheck() {
        super("AutoTotem");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        // Event-driven via recordTotemUse().
    }

    /** Called after a totem successfully saved the player. */
    public void recordTotemUse(Player player, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;

        CheckConfig cc = ctx.cfg().check("autototem");
        double minConfirm = cc.v2() > 0 ? cc.v2() : 5.0;

        // Reaction-time threshold: an auto-totem re-equips faster than even a
        // very skilled player can react after the pop. Min reaction ~120ms; we
        // subtract a generous ping allowance and use a conservative ~60ms.
        long reactionMs = cc.v1() > 0 ? (long) cc.v1() : 60L;
        long sinceSwap = ctx.data().millisSinceOffhandSwap();
        int windowSwaps = ctx.data().emergencyTotemSwaps();

        boolean implausibleReaction = sinceSwap < reactionMs;

        if (implausibleReaction) {
            double buf = ctx.data().adjustBuffer("autototem", 1.5, 32.0);
            if (buf >= minConfirm) {
                flag(ctx, 0.6, 0.82,
                        "totemSwapReaction=" + sinceSwap + "ms (<" + reactionMs + ")"
                                + " emergencySwaps=" + windowSwaps
                                + " ping=" + ctx.ping()
                                + " buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("autototem", -1.0, 32.0);
        }
    }

    /** Record that an off-hand swap to a totem happened. */
    public void recordOffhandTotemSwap(Player player, CheckContext ctx) {
        ItemStack off = safeOffhand(player);
        if (off != null && off.getType() == Material.TOTEM_OF_UNDYING) {
            ctx.data().recordOffhandSwap();
        }
    }

    private static ItemStack safeOffhand(Player player) {
        try {
            if (player.getInventory() == null) return null;
            ItemStack it = player.getInventory().getItemInOffHand();
            return it != null && it.getType() != Material.AIR ? it : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
