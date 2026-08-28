package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.physics.MovementSnapshot;
import me.larping.anticheat.util.CollisionUtil;

/**
 * No-clip / phase detection by collision-shape path sampling.
 *
 * <p>Walks the segment from the previous to current position and asks the real
 * block collision shape whether the player box at each sample intersects solid
 * material — so partial blocks (slabs, stairs, fences, open doors, trapdoors,
 * scaffolding) are handled by physics instead of fragile material-name checks.
 * Expensive sampling only runs when the player moved far enough per packet to
 * phase (normal walking samples nothing). Teleport/hard-grace resyncs are
 * skipped. Multiple confirmations required before flagging.
 */
public final class PhaseCheck extends MovementCheck {

    public PhaseCheck() {
        super("Phase");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;
        MovementSnapshot s = ctx.move();
        if (s == null) return;

        CheckConfig cc = ctx.cfg().check("phase");
        double minConfirm = cc.v1() > 0 ? cc.v1() : 3.0;

        var from = s.from();
        var to = s.to();
        if (from == null || to.getWorld() == null
                || (from.getWorld() != null && !from.getWorld().equals(to.getWorld()))) {
            return;
        }

        double moved = s.hSpeed > Math.abs(s.deltaY) ? s.hSpeed
                : Math.hypot(s.hSpeed, s.deltaY);

        // Normal walking (~0.3 b/t) never needs path sampling.
        if (moved < 1.0) {
            if (CollisionUtil.isInsideBlock(to)) {
                recordFlag(ctx, cc, "embedded");
            } else {
                ctx.data().adjustBuffer("phase", -2.0, 64.0);
            }
            return;
        }

        // Large teleports are covered by teleport grace / Blink setback.
        if (moved > 8.0) return;

        double step = cc.v2() <= 0 ? 0.5 : cc.v2();
        if (CollisionUtil.pathCollides(from, to, Math.max(0.3, step))) {
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
