package me.larping.anticheat.checks.combat;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.modifiers.Capabilities;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Combat automation detection: <b>No Hit Delay</b> and <b>Trigger Bot / Aim
 * Assist cadence</b>.
 *
 * <ul>
 *   <li><b>No Hit Delay</b>: since the 1.9 combat update a weapon only deals
 *       full damage after a cooldown that scales with attack speed (~0.625s
 *       for a sword, faster for axes on the right timing, capped by custom
 *       attributes). Landing repeated full-damage hits well below the
 *       cooldown means the cooldown is being bypassed. Custom weapons/effects
 *       widen the limit via {@link Capabilities}.</li>
 *   <li><b>Trigger/Aim cadence</b>: humans have variable attack timing; a
 *       triggerbot / aim-assist clicks with near-zero jitter (very low
 *       coefficient of variation) sustained over many hits.</li>
 * </ul>
 *
 * Both use rolling evidence and are ping/TPS slack-aware, so fast-but-human
 * play and custom attack-speed gear do not flag.
 */
public final class CombatAutomationCheck extends CombatCheck {

    public CombatAutomationCheck() {
        super("CombatAutomation");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        // Driven by attack events.
    }

    public void evaluateAttack(Player attacker, LivingEntity target, Capabilities caps, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;

        long interval = ctx.data().recordAttackCadence();

        // --- 1) No hit delay: hit landed faster than the weapon cooldown. ---
        // Minimum cooldown ms for the held item (generous floor). Sword ~500ms,
        // a fast/custom attack-speed item can be much lower (read via caps if
        // provided). Leave slack for ping/queue reordering.
        double minCd = 220.0; // absolute floor even for fastest legit weapon
        if (ctx.cfg().compensatePing() && ctx.ping() > 80) {
            minCd -= Math.min(60, (ctx.ping() - 80) / 4.0);
        }
        if (caps != null && caps.movementSpeed > 0) {
            // Custom fast attacks are legitimately quicker; don't floor them out.
            minCd = Math.min(minCd, 120.0);
        }
        if (interval < minCd && interval > 0) {
            ctx.data().recordNoHitDelay();
            double buf = ctx.data().adjustBuffer("nohitdelay", 1.0, 32.0);
            if (buf >= 4) {
                flag(ctx, 0.7, 0.88,
                        "noHitDelay interval=" + interval + "ms min~" + (int) minCd
                                + "ms buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("nohitdelay", -1.0, 32.0);
        }

        // --- 2) Robotic cadence (triggerbot / aim assist). ---
        double cv = ctx.data().attackIntervalVariance();
        // cv < ~0.08 sustained over many hits is metronomic (human CV is
        // typically 0.15+ even for very consistent PvP).
        if (cv < 0.08 && ctx.data().avgAttackInterval() < 1000) {
            double buf = ctx.data().adjustBuffer("triggerbot", 1.0, 32.0);
            if (buf >= 3) {
                flag(ctx, 0.6, 0.82,
                        "roboticCadence cv=" + String.format("%.3f", cv)
                                + " avg=" + (int) ctx.data().avgAttackInterval()
                                + "ms buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("triggerbot", -1.0, 32.0);
        }
    }
}
