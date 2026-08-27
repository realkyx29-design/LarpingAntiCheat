package me.larping.anticheat.checks.combat;

import me.larping.anticheat.checks.Check;
import me.larping.anticheat.checks.CheckContext;
import org.bukkit.entity.Player;

/**
 * Base class for combat checks (Reach, AutoClicker, etc.).
 */
public abstract class CombatCheck implements Check {
    protected final String checkName;

    protected CombatCheck(String checkName) {
        this.checkName = checkName;
    }

    @Override
    public String name() {
        return checkName;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public abstract void evaluate(Player player, CheckContext context);
}
