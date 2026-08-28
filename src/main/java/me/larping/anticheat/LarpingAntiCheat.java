package me.larping.anticheat;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.commands.LacCommand;
import me.larping.anticheat.config.ConfigManager;
import me.larping.anticheat.data.PlayerData;
import me.larping.anticheat.listeners.AntiCheatListener;
import me.larping.anticheat.managers.CheckManager;
import me.larping.anticheat.managers.FlightEnforcer;
import me.larping.anticheat.managers.ViolationManager;
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

    private volatile double currentTps = 20.0;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.violationManager = new ViolationManager(this);
        this.notifier = new Notifier(this);
        this.capabilities = new CapabilityAnalyzer();
        this.checkManager = new CheckManager();
        this.flightEnforcer = new FlightEnforcer(this);

        getServer().getPluginManager().registerEvents(new AntiCheatListener(this), this);

        LacCommand lacCommand = new LacCommand(this);
        if (getCommand("lac") != null) {
            getCommand("lac").setExecutor(lacCommand);
            getCommand("lac").setTabCompleter(lacCommand);
        }

        // Per-tick main-thread flush: timer balance evaluation for each
        // online player. Cheap (no YAML, minimal arithmetic).
        getServer().getScheduler().runTaskTimer(this, this::tickChecks, 1L, 1L);

        // Once per second: TPS sampling and violation decay. Async-safe
        // because decay only touches concurrent structures and TPS is read
        // from the server API.
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                double[] tps = getServer().getTPS();
                if (tps != null && tps.length > 0) {
                    currentTps = Math.min(20.0, Math.max(0.0, tps[0]));
                }
                violationManager.decay();
            } catch (Throwable ignored) {
                // Never let a maintenance task take down the plugin.
            }
        }, 20L, 20L);

        notifier.banner(checkManager.all().size());
    }

    /** Runs once per server tick on the main thread. */
    private void tickChecks() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                PlayerData data = playerDataMap.get(player.getUniqueId());
                if (data == null) continue;
                // Update smoothed ping so checks use a stable latency value.
                data.smoothPing(player.getPing());
                CheckContext ctx = new CheckContext(this, player, data);
                checkManager.timer().onTick(ctx);
                flightEnforcer.tick(player);
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

    public double tps() {
        return currentTps;
    }
}
