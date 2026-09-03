package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.physics.MovementSnapshot;

/**
 * Flight / hover / glide detection based on gravity.
 *
 * <p>The vanilla game always applies gravity: a player airborne for longer
 * than the longest legitimate jump/glide must be accelerating downward. The
 * old check only flagged near-zero deltaY (perfect hover) and reset its air
 * counter on ANY downward movement, so horizontal flight bypassed it. This
 * version flags, with confirmation buffers and generous per-state envelopes:
 * <ol>
 *   <li>sustained airborne time while NOT falling (glide/horizontal fly),</li>
 *   <li>upward acceleration mid-air with no valid cause (vertical fly),</li>
 *   <li>near-stationary hover high above ground.</li>
 * </ol>
 * Gliding, levitation, slow falling, climbing, liquids, riptide, bounces and
 * knockback are all accounted for via the shared {@link MovementSnapshot}.
 */
public final class FlyCheck extends MovementCheck {

    public FlyCheck() {
        super("Fly");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;
        MovementSnapshot s = ctx.move();
        if (s == null) return;

        // --- Fully legitimate flight / airborne states, never flag ---
        var caps = ctx.data().capabilities();
        // Only SERVER-GRANTED flight is allowed (creative/spectator/donor with
        // getAllowFlight true). A survival fly hack has isFlying() true but
        // getAllowFlight() false — that must NOT exempt.
        boolean serverFlight = (caps != null && caps.allowedFlight)
                || CheckContext.serverAllowsFlight(ctx.player());
        if (serverFlight) { decay(ctx); return; }
        // Elytra gliding is its own movement model and is always allowed.
        if (s.gliding || (caps != null && caps.gliding)) { decay(ctx); return; }
        // Levitation / riptide are legitimate upward motion.
        if (s.levitation || s.riptide) { decay(ctx); return; }
        // Liquids, climbing, webs use different physics.
        if (s.feetInLiquid || s.headInLiquid || s.inLava || s.onClimbable || s.inWeb) {
            decay(ctx); return;
        }

        CheckConfig cc = ctx.cfg().check("fly");
        double minConfirm = cc.v2() > 0 ? cc.v2() : 4.0;

        double dY = s.deltaY;
        double lastDY = s.lastDeltaY;
        // Custom-movement allowance: custom-mod config plus equipment that
        // grants jump/air mobility (custom boots / feather enchantments).
        double customAir = (ctx.cfg().customModsEnabled() && ctx.cfg().customMovementComp()) ? 6 : 0;
        if (caps != null && caps.jumpMultiplier > 1.05) {
            customAir += 10 + (int) (caps.jumpMultiplier * 6);
        }
        int maxAir = s.maxSustainedAirTicks(customAir);

        boolean airborne = !s.serverGround;
        boolean violation = false;
        String reason = null;

        // 1) Sustained flight: airborne far past any jump and not falling.
        if (airborne && s.airTicks > maxAir && dY >= -0.06) {
            violation = true;
            reason = "sustained-air airTicks=" + s.airTicks + " dY=" + f(dY) + " max=" + maxAir;
        }
        // 2) Upward acceleration mid-air (gravity must slow the rise).
        else if (airborne && s.airTicks > 3 && dY > 0.42 && lastDY >= dY) {
            boolean caused = s.jumpAmplifier >= 0 || s.hasVelocity || s.onBouncy
                    || (caps != null && caps.jumpMultiplier > 1.05);
            if (!caused) {
                violation = true;
                reason = "upward-accel dY=" + f(dY) + " lastDY=" + f(lastDY) + " air=" + s.airTicks;
            }
        }
        // 3) Classic hover: essentially stationary in mid-air.
        else if (airborne && s.airTicks > 12 && s.hSpeed < 0.02 && Math.abs(dY) < 0.02) {
            violation = true;
            reason = "hover airTicks=" + s.airTicks + " dY=" + f(dY);
        }

        if (violation) {
            double buf = ctx.data().adjustBuffer("fly", 1.0, 64.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                        0.5, 0.9, reason + " buffer=" + (int) buf,
                        me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            }
        } else {
            decay(ctx);
        }
    }

    private void decay(CheckContext ctx) {
        ctx.data().adjustBuffer("fly", -2.0, 64.0);
    }

    private static String f(double d) {
        return String.format("%.3f", d);
    }
}
