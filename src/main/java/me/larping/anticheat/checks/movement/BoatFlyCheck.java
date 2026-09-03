package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.physics.MovementSnapshot;

/**
 * Boat / vehicle flight exploit detection.
 *
 * <p>Legitimate boats sit on water (small vertical bob) or fall under gravity
 * when in open air. Boat-fly cheats hold a boat aloft or climb with no liquid
 * and no ground beneath. This runs for players who are inside a vehicle (which
 * other movement checks skip) and validates the vehicle's vertical motion: a
 * sustained altitude with no water/ground under the boat is the exploit;
 * normal boating, water bobbing, sloping shorelines and knockback are allowed.
 */
public final class BoatFlyCheck extends MovementCheck {

    public BoatFlyCheck() {
        super("BoatFly");
    }

    @Override
    protected boolean exempt(CheckContext ctx) {
        // BoatFly specifically inspects vehicles, so do NOT skip on
        // isInsideVehicle; still skip creative/spectator/dead/bypass/grace.
        return ctx.isFullyExempt();
    }

    @Override
    public void evaluate(CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;
        MovementSnapshot s = ctx.move();
        if (s == null) return;

        // Only process vehicle movement; non-vehicle players are covered by
        // the normal Fly check.
        if (!s.insideVehicle) {
            ctx.data().resetVehicle();
            return;
        }

        // Spectator/creative/allowed-flight vehicles are fine.
        var caps = ctx.data().capabilities();
        if (caps != null && (caps.creative || caps.spectator || caps.allowedFlight)) {
            ctx.data().resetVehicle();
            return;
        }

        // Grace after teleport / velocity (server moving the boat legitimately).
        if (s.riptide || s.hasVelocity || ctx.data().inHardGrace()) {
            ctx.data().resetVehicle();
            return;
        }

        CheckConfig cc = ctx.cfg().check("boatfly");
        double minConfirm = cc.v2() > 0 ? cc.v2() : 8.0;

        // Whether the boat is over liquid (water/lily) or a solid surface —
        // that is where boats legitimately stay afloat.
        boolean supported = s.feetInLiquid || s.headInLiquid || s.inWater || s.serverGround
                || s.belowBlock != null && (s.belowBlock.isLiquid()
                        || s.belowBlock.getType().name().contains("LILY")
                        || s.belowBlock.getType().name().contains("WATER"));

        ctx.data().updateVehicle(s.to().getY(), supported);

        int airTicks = ctx.data().vehicleAirTicks();
        double dY = ctx.data().vehicleDeltaY();

        // Boat-fly: held aloft for many ticks with no support and no real fall.
        boolean violation = !supported && airTicks > minConfirm && dY >= -0.05;
        if (violation) {
            double buf = ctx.data().adjustBuffer("boatfly", 1.0, 64.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                        0.5, 0.85,
                        "boatAir=" + airTicks + " dY=" + String.format("%.3f", dY)
                                + " hSpeed=" + String.format("%.2f", s.hSpeed)
                                + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            }
        } else {
            ctx.data().adjustBuffer("boatfly", -1.0, 64.0);
        }
    }
}
