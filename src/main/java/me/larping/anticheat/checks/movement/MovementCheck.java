package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.Check;
import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.managers.ViolationManager;

/**
 * Base class for movement-related checks. Provides shared exemption handling
 * and buffered flag helpers so individual checks stay small and consistent.
 */
public abstract class MovementCheck implements Check {
    protected final String checkName;
    protected final String bufferKey;

    protected MovementCheck(String checkName) {
        this.checkName = checkName;
        this.bufferKey = checkName.toLowerCase();
    }

    @Override
    public String name() {
        return checkName;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    /** Standard movement exemption: vehicles, flight, gamemodes, hard grace. */
    protected boolean exempt(CheckContext ctx) {
        return ctx.isMovementExempt();
    }

    /** Returns true when the check is enabled in the cached config. */
    protected boolean checkEnabled(CheckContext ctx) {
        return ctx.cfg().checkEnabled(bufferKey);
    }

    /**
     * Increments the check's confirmation buffer and flags when it reaches the
     * configured minimum. On a clean pass, decays the buffer.
     */
    protected void bufferedFlag(CheckContext ctx, boolean violation, double vlAmount,
                                double confidence, String detail, double minConfirm) {
        double buf;
        if (violation) {
            buf = ctx.data().adjustBuffer(bufferKey, 1.0, 64.0);
        } else {
            ctx.data().adjustBuffer(bufferKey, -1.5, 64.0);
            return;
        }
        if (buf >= minConfirm) {
            ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                    vlAmount, confidence, detail, ViolationManager.Setback.MOVEMENT);
        }
    }

    /** Flags immediately (no buffer) — used for high-confidence events like blink. */
    protected void flag(CheckContext ctx, double vlAmount, double confidence, String detail) {
        ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                vlAmount, confidence, detail, ViolationManager.Setback.MOVEMENT);
    }
}
