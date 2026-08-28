package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.physics.MovementSnapshot;

/**
 * Physics-based horizontal speed check.
 *
 * <p>The limit is derived from the player's real server-side movement speed
 * attribute (which automatically accounts for custom SMP items/passives),
 * sprint-jump physics and speed potions, with additive, bounded slack for
 * latency/TPS — so a cheat cannot sneak through by stacking multipliers (the
 * old design multiplied sprint × ice × custom-mod × ping × TPS into a ~7×
 * ceiling), while legitimate speed modifiers are always honoured.
 *
 * <p>Gliding, liquids, cobwebs, climbables and riptide are bounded by their
 * own dedicated envelopes/checks rather than a generous ground limit.
 */
public final class SpeedCheck extends MovementCheck {

    public SpeedCheck() {
        super("Speed");
    }

    @Override
    public void evaluate(me.larping.anticheat.checks.CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) return;
        MovementSnapshot s = ctx.move();
        if (s == null) return;

        CheckConfig cc = ctx.cfg().check("speed");
        double minConfirm = cc.v2() > 0 ? cc.v2() : 6.0;

        if (s.gliding) {
            evaluateGlide(ctx, s, cc, minConfirm);
            return;
        }

        double speed = s.hSpeed;
        if (speed < 1.0e-4) return; // duplicate / near-look packet

        double limit = s.maxGroundHorizontalSpeed(
                ctx.ping(), ctx.tps(),
                ctx.cfg().compensatePing(), ctx.cfg().compensateTps(),
                (ctx.cfg().customModsEnabled() && ctx.cfg().customMovementComp()) ? 1.10 : 1.0);

        if (limit == Double.POSITIVE_INFINITY) {
            // Liquid/web/climb: covered by their own tight envelopes; decay.
            if (s.feetInLiquid || s.headInLiquid || s.inWeb) {
                evaluateLiquid(ctx, s, minConfirm);
            }
            return;
        }

        double excess = speed - limit;
        boolean violation = excess > 0.0;

        if (violation) {
            double confidence = Math.min(0.97, 0.62 + excess * 1.6);
            double buf = ctx.data().adjustBuffer("speed", 1.0, 64.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                        Math.min(1.0, 0.35 + excess * 2.2), confidence,
                        "hSpeed=" + f(speed) + " limit=" + f(limit)
                                + " base=" + f(s.baseSpeed) + " ground=" + s.serverGround
                                + " ping=" + ctx.ping() + " tps=" + String.format("%.1f", ctx.tps()),
                        me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            }
        } else {
            ctx.data().adjustBuffer("speed", -1.5, 64.0);
        }
    }

    /** Tight horizontal envelope while submerged in liquid or in a cobweb. */
    private void evaluateLiquid(CheckContext ctx, MovementSnapshot s, double minConfirm) {
        double limit;
        if (s.inWeb) limit = 0.10;
        else if (s.headInLiquid) limit = 0.13; // fully submerged swim cap
        else limit = 0.22;                    // wading / surface
        // active velocity (e.g. water current / riptide launch) adds directly;
        // speed potion is already reflected in s.baseSpeed (attribute value).
        limit += s.velocityH;
        // Custom SMP movement modifiers also flow through baseSpeed, so only a
        // small sprint factor remains for surface wading.
        if (s.sprinting) limit *= 1.15;

        boolean violation = s.hSpeed > limit;
        if (violation) {
            double excess = s.hSpeed - limit;
            double buf = ctx.data().adjustBuffer("speed", 1.0, 64.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                        Math.min(1.0, 0.3 + excess * 2.0), 0.85,
                        "liquid hSpeed=" + f(s.hSpeed) + " limit=" + f(limit)
                                + (s.inWeb ? " web" : " water"),
                        me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            }
        } else {
            ctx.data().adjustBuffer("speed", -1.5, 64.0);
        }
    }

    /** Elytra speed envelope — generous but bounded, with firework/riptide allowances. */
    private void evaluateGlide(CheckContext ctx, MovementSnapshot s, CheckConfig cc, double minConfirm) {
        double speed = s.hSpeed;
        double limit = 1.95;                 // sustained elytra glide
        if (s.fireworkBoost) limit = 2.7;    // firework rocket boost
        if (s.riptide) limit = 3.3;          // trident launch
        limit += s.velocityH;
        if (ctx.cfg().compensatePing() && ctx.ping() > 80)
            limit += Math.min(0.3, (ctx.ping() - 80) / 900.0);
        if (ctx.cfg().compensateTps() && ctx.tps() < 19.0)
            limit += Math.min(0.4, (20.0 - ctx.tps()) * 0.08);

        boolean violation = speed > limit;
        if (violation) {
            double excess = speed - limit;
            double buf = ctx.data().adjustBuffer("speed", 1.0, 64.0);
            if (buf >= minConfirm) {
                ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                        Math.min(1.0, 0.4 + excess), Math.min(0.97, 0.70 + excess * 0.9),
                        "elytra hSpeed=" + f(speed) + " limit=" + f(limit)
                                + " firework=" + s.fireworkBoost,
                        me.larping.anticheat.managers.ViolationManager.Setback.MOVEMENT);
            }
        } else {
            ctx.data().adjustBuffer("speed", -1.5, 64.0);
        }
    }

    private static String f(double d) {
        return String.format("%.3f", d);
    }
}
