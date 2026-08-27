package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.util.CollisionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * No-clip / phase detection by collision-shape path sampling.
 *
 * <p>The old check only inspected the blocks at the destination (so a quick
 * blink through a wall never had solid blocks at either end) and exempted
 * doors/gates/etc by material name, which let players phase through them
 * legitimately-closed. This version:
 * <ol>
 *   <li>only runs expensive sampling when the player actually moved a
 *       meaningful distance (normal walking samples nothing),</li>
 *   <li>walks the segment from the previous to current position and asks the
 *       real block {@link org.bukkit.block.Block#getCollisionShape()} whether
 *       the player box at each sample intersects — partial blocks like slabs,
 *       stairs, fences, open doors and trapdoors are automatically handled,</li>
 *   <li>requires multiple confirmations and skips hard grace (teleport).</li>
 * </ol>
 */
public final class PhaseCheck extends MovementCheck {

    public PhaseCheck() {
        super("Phase");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;

        CheckConfig cc = ctx.cfg().check("phase");
        // prevLocation is the position BEFORE this move (lastLocation is
        // already advanced to 'to' by updateMovement).
        Location from = ctx.data().prevLocation();
        Location to = player.getLocation();
        if (from == null || to.getWorld() == null
                || (from.getWorld() != null && !from.getWorld().equals(to.getWorld()))) {
            return;
        }

        // Fast path: normal walking speed (~0.3 b/t) never needs sampling.
        // Only a speed/blink cheat moves far enough per packet to phase.
        double moved = to.distance(from);
        if (moved < 1.0) {
            // Near-stationary: a single cheap endpoint-embeddedness check.
            if (CollisionUtil.isInsideBlock(to)) {
                recordFlag(ctx, cc, "embedded");
            } else {
                ctx.data().adjustBuffer("phase", -2.0, 64.0);
            }
            return;
        }

        // Large teleports are covered by teleport grace / setback by Blink.
        if (moved > 8.0) return;

        double step = cc.v2() <= 0 ? 0.5 : cc.v2();
        boolean collides = CollisionUtil.pathCollides(from, to, Math.max(0.3, step));
        if (collides) {
            recordFlag(ctx, cc, "pathCollide dist=" + String.format("%.2f", moved));
        } else {
            ctx.data().adjustBuffer("phase", -2.0, 64.0);
        }
    }

    private void recordFlag(CheckContext ctx, CheckConfig cc, String detail) {
        double buf = ctx.data().adjustBuffer("phase", 1.0, 64.0);
        if (buf >= cc.v1()) {
            ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                    0.6, 0.9, detail + " buffer=" + (int) buf,
                    me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
        }
    }
}
