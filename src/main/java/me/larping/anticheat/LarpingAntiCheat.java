package me.larping.anticheat;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.commands.LacCommand;
import me.larping.anticheat.config.ConfigManager;
import me.larping.anticheat.data.PlayerData;
import me.larping.anticheat.listeners.AntiCheatListener;
import me.larping.anticheat.managers.CheckManager;
import me.larping.anticheat.managers.FlightEnforcer;
import me.larping.anticheat.managers.ViolationManager;
import me.larping.anticheat.honeypot.DecoyService;
import me.larping.anticheat.honeypot.Honeypot;
import me.larping.anticheat.checks.honeypot.EspCheck;
import me.larping.anticheat.modifiers.CapabilityAnalyzer;
import me.larping.anticheat.notify.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main plugin class for LarpingAntiCheat.
 *
 * <p>Performance summary:
 * <ul>
 *   <li>Config is parsed once into an immutable snapshot on enable/reload —
 *       the movement hot path performs zero YAML lookups.</li>
 *   <li>Checks are singletons owned by {@link CheckManager}; the listener and
 *       tasks all reuse them (the old listener created duplicate instances).</li>
 *   <li>Look-only move packets skip all movement work.</li>
 *   <li>The per-tick task only flushes the timer balance for online players;
 *       TPS sampling and violation decay run once per second.</li>
 * </ul>
 */
public final class LarpingAntiCheat extends JavaPlugin {

    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    private ConfigManager configManager;
    private ViolationManager violationManager;
    private CheckManager checkManager;
    private Notifier notifier;
    private CapabilityAnalyzer capabilities;
    private FlightEnforcer flightEnforcer;
    private DecoyService decoyService;
    private Honeypot honeypot;
    private EspCheck espCheck;

    private volatile double currentTps = 20.0;

    @Override
    public void onEnable() {
        // Each subsystem is guarded so a failure in one (e.g. a renamed API
        // on an unexpected Paper build) is logged instead of preventing the
        // plugin from enabling — the server always starts.
        safe("config", () -> this.configManager = new ConfigManager(this));
        safe("violations", () -> this.violationManager = new ViolationManager(this));
        safe("notifier", () -> this.notifier = new Notifier(this));
        safe("capabilities", () -> this.capabilities = new CapabilityAnalyzer());
        safe("checks", () -> this.checkManager = new CheckManager());
        safe("flight-enforcer", () -> this.flightEnforcer = new FlightEnforcer(this));
        safe("decoy-service", () -> this.decoyService = new DecoyService(this));
        safe("honeypot", () -> {
            if (decoyService != null) this.honeypot = new Honeypot(decoyService);
        });
        safe("esp-check", () -> {
            if (decoyService != null) this.espCheck = new EspCheck(decoyService);
        });

        safe("listener", () -> getServer().getPluginManager()
                .registerEvents(new AntiCheatListener(this), this));

        safe("command", () -> {
            LacCommand lacCommand = new LacCommand(this);
            if (getCommand("lac") != null) {
                getCommand("lac").setExecutor(lacCommand);
                getCommand("lac").setTabCompleter(lacCommand);
            }
        });

        // Per-tick main-thread flush (timer balance, flight authority, esp).
        safe("scheduler", () -> getServer().getScheduler()
                .runTaskTimer(this, this::tickChecks, 1L, 1L));

        // Once per second: TPS sampling and violation decay.
        safe("async-scheduler", () -> getServer().getScheduler()
                .runTaskTimerAsynchronously(this, () -> {
                    try {
                        double[] tps = getServer().getTPS();
                        if (tps != null && tps.length > 0) {
                            currentTps = Math.min(20.0, Math.max(0.0, tps[0]));
                        }
                        if (violationManager != null) violationManager.decay();
                    } catch (Throwable ignored) {
                        // Never let a maintenance task take down the plugin.
                    }
                }, 20L, 20L));

        safe("banner", () -> {
            if (notifier != null && checkManager != null) notifier.banner(checkManager.all().size());
        });

        getLogger().info("Hyphon enabled.");
    }

    /** Runs a startup step and logs any error instead of aborting enable. */
    private void safe(String name, Runnable step) {
        try {
            step.run();
        } catch (Throwable t) {
            getLogger().severe("Hyphon subsystem '" + name + "' failed to start (plugin still enabled): "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /** Runs once per server tick on the main thread. */
    private void tickChecks() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                PlayerData data = playerDataMap.get(player.getUniqueId());
                if (data == null) continue;
                // Update smoothed ping so checks use a stable latency value.
                try { data.smoothPing(player.getPing()); } catch (Throwable ignored) { }
                if (checkManager != null) {
                    CheckContext ctx = new CheckContext(this, player, data);
                    try { checkManager.timer().onTick(ctx); } catch (Throwable ignored) { }
                }
                if (flightEnforcer != null) {
                    try { flightEnforcer.tick(player); } catch (Throwable ignored) { }
                }
                if (espCheck != null) {
                    try { espCheck.evaluate(new CheckContext(this, player, data)); } catch (Throwable ignored) { }
                }
            } catch (Throwable t) {
                // A single failing check must never break movement processing.
                getLogger().warning("Check tick error for " + player.getName() + ": " + t);
            }
        }
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        playerDataMap.clear();
        getLogger().info("LarpingAntiCheat disabled.");
    }

    public PlayerData data(Player player) {
        return playerDataMap.computeIfAbsent(player.getUniqueId(), k -> new PlayerData(player));
    }

    public void remove(Player player) {
        playerDataMap.remove(player.getUniqueId());
        violationManager.clear(player);
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public ViolationManager violations() {
        return violationManager;
    }

    public CheckManager checkManager() {
        return checkManager;
    }

    public Notifier notifier() {
        return notifier;
    }

    public CapabilityAnalyzer capabilities() {
        return capabilities;
    }

    public FlightEnforcer flightEnforcer() {
        return flightEnforcer;
    }

    public Honeypot honeypot() {
        return honeypot;
    }

    public DecoyService decoys() {
        return decoyService;
    }

    public EspCheck esp() {
        return espCheck;
    }

    public double tps() {
        return currentTps;
    }
}
