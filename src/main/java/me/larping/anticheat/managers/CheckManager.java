package me.larping.anticheat.managers;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.checks.Check;
import me.larping.anticheat.checks.combat.ReachCheck;
import me.larping.anticheat.checks.combat.RotationCheck;
import me.larping.anticheat.checks.movement.FlyCheck;
import me.larping.anticheat.checks.movement.FreecamCheck;
import me.larping.anticheat.checks.movement.GroundSpoofCheck;
import me.larping.anticheat.checks.movement.PhaseCheck;
import me.larping.anticheat.checks.movement.SpeedCheck;
import me.larping.anticheat.checks.movement.TimerCheck;
import me.larping.anticheat.checks.world.ScaffoldCheck;
import java.util.*;

public final class CheckManager {
    private final Map<String, Check> checks = new LinkedHashMap<>();

    public CheckManager(LarpingAntiCheat plugin) {
        register(new SpeedCheck());
        register(new FlyCheck());
        register(new ReachCheck());
        register(new ScaffoldCheck());
        register(new TimerCheck());
        register(new GroundSpoofCheck());
        register(new PhaseCheck());
        register(new RotationCheck());
        register(new FreecamCheck());
    }

    public void register(Check check) {
        checks.put(check.name().toLowerCase(), check);
    }

    public Check get(String name) {
        return checks.get(name.toLowerCase());
    }

    public Collection<Check> all() {
        return checks.values();
    }
}
