package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.util.CollisionUtil;
import org.bukkit.entity.Player;

/**
 * Ground-state spoofing (NoFall and ground-lying cheat clients).
 *
 * <p>The old check was logically dead: {@code airTicks} was reset to 0 on
 * every move where {@code player.isOnGround()} was true, so its condition
 * {@code isOnGround() && airTicks > 10} could never hold.
 *
 * <p>Bukkit's {@link Player#isOnGround()} reflects the server's own collision
 * state in modern Paper (a spoofed client onGround flag is rejected). We
 * therefore compare that server ground state against an independent
 * collision-shape computation: the real tell of NoFall is a player clearly
 * airborne and descending yet *never accumulating* vertical fall — i.e. the
 * client resetting fall state. Here we flag the impossible combination
 * "server says onGround, collision check says no ground, while descending".
 */
public final class GroundSpoofCheck extends MovementCheck {

    public GroundSpoofCheck() {
        super("GroundSpoof");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;

        CheckConfig cc = ctx.cfg().check("groundspoof");
        double deltaY = ctx.data().deltaY();
        int airTicks = ctx.data().airTicks();

        boolean clientGround = player.isOnGround();
        boolean collisionGround = CollisionUtil.isOnGround(player.getLocation());

        boolean impossible = false;
        String detail = null;

        if (clientGround && !collisionGround && deltaY < -0.42 && airTicks > 6) {
            impossible = true;
            detail = "claimsGround while falling dY=" + String.format("%.3f", deltaY)
                    + " airTicks=" + airTicks;
        }

        if (impossible) {
            double buf = ctx.data().adjustBuffer("groundspoof", 1.0, 32.0);
            if (buf >= cc.v1()) {
                ctx.plugin().violations().flag(player, checkName, "Movement",
                        0.45, 0.85, detail + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.NONE);
            }
        } else {
            ctx.data().adjustBuffer("groundspoof", -2.0, 32.0);
        }
    }
}
