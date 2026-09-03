package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.physics.MovementSnapshot;

/**
 * Ground-state spoofing (NoFall / lying about ground contact).
 *
 * <p>The old check was logically dead: {@code airTicks} was reset to 0 on every
 * move where {@code player.isOnGround()} was true, so its condition could never
 * hold. Modern Paper already derives ground state from real collision, so this
 * check only flags the <b>impossible</b> combination: the client claims to be on
 * the ground while the player is clearly airborne and falling fast (NoFall
 * clients spam onGround to avoid fall damage). Airborne/descending players are
 * never flagged for being airborne — only the contradictory ground claim.
 */
public final class GroundSpoofCheck extends MovementCheck {

    public GroundSpoofCheck() {
        super("GroundSpoof");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;
        MovementSnapshot s = ctx.move();
        if (s == null) return;

        // Only meaningful when genuinely airborne; liquids/climb/levitation
        // produce ambiguous ground states and are exempt.
        if (s.serverGround || s.feetInLiquid || s.onClimbable || s.levitation
                || s.gliding || s.riptide || s.slowFalling) {
            ctx.data().adjustBuffer("groundspoof", -2.0, 32.0);
            return;
        }

        CheckConfig cc = ctx.cfg().check("groundspoof");
        double minConfirm = cc.v1() > 0 ? cc.v1() : 3.0;

        // Client falsely claims ground contact while falling in mid-air.
        boolean impossible = s.clientGround && s.airTicks > 6 && s.deltaY < -0.42;

        if (impossible) {
            double buf = ctx.data().adjustBuffer("groundspoof", 1.0, 32.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                        0.45, 0.85,
                        "claimsGround while falling dY=" + f(s.deltaY) + " air=" + s.airTicks
                                + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.NONE);
            }
        } else {
            ctx.data().adjustBuffer("groundspoof", -2.0, 32.0);
        }
    }

    private static String f(double d) {
        return String.format("%.3f", d);
    }
}
