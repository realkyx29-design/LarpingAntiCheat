package me.larping.anticheat.checks.combat;

import me.larping.anticheat.checks.Check;
import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.managers.ViolationManager;

/**
 * Base class for combat-related checks (Reach, KillAura, WeaponDamage).
 */
public abstract class CombatCheck implements Check {
    protected final String checkName;
    protected final String bufferKey;

    protected CombatCheck(String checkName) {
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

    protected boolean checkEnabled(CheckContext ctx) {
        return ctx.cfg().checkEnabled(bufferKey);
    }

    protected void flag(CheckContext ctx, double vlAmount, double confidence, String detail) {
        ctx.plugin().violations().flag(ctx.player(), checkName, "Combat",
                vlAmount, confidence, detail, ViolationManager.Setback.NONE);
    }
}
