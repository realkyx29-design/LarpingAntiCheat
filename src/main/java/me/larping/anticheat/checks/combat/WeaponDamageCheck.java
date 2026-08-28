package me.larping.anticheat.checks.combat;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.modifiers.Capabilities;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Weapon damage validation.
 *
 * <p>Instead of one hard-coded maximum damage for every weapon, this computes
 * the highest damage the player's <b>actual held weapon</b> can produce —
 * base damage from the item's attack-damage attribute, Sharpness, Strength,
 * critical hits and recognised custom-enchant bonuses (see
 * {@link Capabilities#maxWeaponDamage}). A hit that materially exceeds that is
 * an invalid (spiked) damage value: it is reported, and once sustained the
 * hit is cancelled so the excess damage never applies.
 *
 * <p>Custom swords that legitimately hit harder are reflected in
 * {@code maxWeaponDamage} via item attribute modifiers, display-text scanning
 * or a registered {@code CustomModifierProvider}, so they never false-flag.
 */
public final class WeaponDamageCheck extends CombatCheck {

    public WeaponDamageCheck() {
        super("WeaponDamage");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        // Driven by attack events.
    }

    public void evaluateAttack(Player attacker, EntityDamageByEntityEvent event,
                               Capabilities caps, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;
        if (caps == null) return;

        CheckConfig cc = ctx.cfg().check("weapondamage");
        double headroom = cc.v1() > 0 ? cc.v1() : 4.0;
        double minConfirm = cc.v2() > 0 ? cc.v2() : 3.0;

        double dealt = event.getFinalDamage();
        double max = caps.maxWeaponDamage;
        if (max <= 0) return;

        double ceiling = max + headroom;
        if (dealt <= ceiling) {
            ctx.data().adjustBuffer("weapondamage", -2.0, 32.0);
            return;
        }

        double excess = dealt - ceiling;
        double buf = ctx.data().adjustBuffer("weapondamage", 1.0, 32.0);
        if (buf >= minConfirm) {
            flag(ctx,
                    Math.min(1.0, 0.4 + excess * 0.05), 0.92,
                    "damage=" + String.format("%.1f", dealt)
                            + " maxWeapon=" + String.format("%.1f", max)
                            + " ceiling=" + String.format("%.1f", ceiling)
                            + " buffer=" + (int) buf);
            // Cancel the spiked hit so the excess damage never applies.
            event.setCancelled(true);
        }
    }
}
