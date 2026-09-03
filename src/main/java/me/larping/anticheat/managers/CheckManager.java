package me.larping.anticheat.managers;

import me.larping.anticheat.checks.Check;
import me.larping.anticheat.checks.combat.KillAuraCheck;
import me.larping.anticheat.checks.combat.ReachCheck;
import me.larping.anticheat.checks.combat.AutoTotemCheck;
import me.larping.anticheat.checks.combat.AutoCrystalCheck;
import me.larping.anticheat.checks.combat.CombatAutomationCheck;
import me.larping.anticheat.checks.combat.WeaponDamageCheck;
import me.larping.anticheat.checks.movement.BlinkCheck;
import me.larping.anticheat.checks.movement.BoatFlyCheck;
import me.larping.anticheat.checks.movement.FlyCheck;
import me.larping.anticheat.checks.movement.GroundSpoofCheck;
import me.larping.anticheat.checks.movement.JesusCheck;
import me.larping.anticheat.checks.movement.NoKnockbackCheck;
import me.larping.anticheat.checks.movement.NoSlowCheck;
import me.larping.anticheat.checks.movement.PhaseCheck;
import me.larping.anticheat.checks.movement.SpeedCheck;
import me.larping.anticheat.checks.movement.SpiderCheck;
import me.larping.anticheat.checks.movement.StepCheck;
import me.larping.anticheat.checks.movement.TimerCheck;
import me.larping.anticheat.checks.world.FastBreakCheck;
import me.larping.anticheat.checks.world.NukerCheck;
import me.larping.anticheat.checks.world.AutoWebCheck;
import me.larping.anticheat.checks.world.ScaffoldCheck;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single registry of all checks. The old listener instantiated its own copies
 * of every check (so the registry's instances were dead weight and each check
 * held duplicate state); this registry is now the only source and exposes
 * typed accessors for the checks that need event-specific entry points.
 */
public final class CheckManager {

    private final Map<String, Check> checks = new LinkedHashMap<>();

    // Typed references for checks with non-standard entry points.
    private final TimerCheck timer;
    private final ReachCheck reach;
    private final KillAuraCheck killAura;
    private final WeaponDamageCheck weaponDamage;
    private final CombatAutomationCheck combatAutomation;
    private final BoatFlyCheck boatFly;
    private final AutoTotemCheck autoTotem;
    private final AutoCrystalCheck autoCrystal;
    private final AutoWebCheck autoWeb;
    private final ScaffoldCheck scaffold;
    private final FastBreakCheck fastBreak;
    private final NukerCheck nuker;

    public CheckManager() {
        timer = register(new TimerCheck());
        register(new SpeedCheck());
        register(new FlyCheck());
        register(new GroundSpoofCheck());
        register(new PhaseCheck());
        register(new JesusCheck());
        register(new SpiderCheck());
        register(new StepCheck());
        register(new BlinkCheck());
        register(new NoKnockbackCheck());
        register(new NoSlowCheck());
        reach = register(new ReachCheck());
        killAura = register(new KillAuraCheck());
        weaponDamage = register(new WeaponDamageCheck());
        combatAutomation = register(new CombatAutomationCheck());
        boatFly = register(new BoatFlyCheck());
        autoTotem = register(new AutoTotemCheck());
        autoCrystal = register(new AutoCrystalCheck());
        autoWeb = register(new AutoWebCheck());
        scaffold = register(new ScaffoldCheck());
        fastBreak = register(new FastBreakCheck());
        nuker = register(new NukerCheck());
    }

    private <T extends Check> T register(T check) {
        checks.put(check.name().toLowerCase(), check);
        return check;
    }

    public Check get(String name) {
        return checks.get(name.toLowerCase());
    }

    public Collection<Check> all() {
        return checks.values();
    }

    public TimerCheck timer() { return timer; }
    public ReachCheck reach() { return reach; }
    public KillAuraCheck killAura() { return killAura; }
    public WeaponDamageCheck weaponDamage() { return weaponDamage; }
    public CombatAutomationCheck combatAutomation() { return combatAutomation; }
    public BoatFlyCheck boatFly() { return boatFly; }
    public AutoTotemCheck autoTotem() { return autoTotem; }
    public AutoCrystalCheck autoCrystal() { return autoCrystal; }
    public AutoWebCheck autoWeb() { return autoWeb; }
    public ScaffoldCheck scaffold() { return scaffold; }
    public FastBreakCheck fastBreak() { return fastBreak; }
    public NukerCheck nuker() { return nuker; }
}
