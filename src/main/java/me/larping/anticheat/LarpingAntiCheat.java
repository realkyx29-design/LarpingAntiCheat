package me.larping.anticheat;

import me.larping.anticheat.commands.LacCommand;
import me.larping.anticheat.config.ConfigManager;
import me.larping.anticheat.data.PlayerData;
import me.larping.anticheat.listeners.AntiCheatListener;
import me.larping.anticheat.managers.CheckManager;
import me.larping.anticheat.managers.ViolationManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main plugin class for LarpingAntiCheat.
 * Conservative, custom-SMP friendly anti-cheat for Paper 1.21.11.
 */
public final class LarpingAntiCheat extends JavaPlugin {
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private ConfigManager configManager;
    private ViolationManager violationManager;
    private CheckManager checkManager;
    private double currentTps = 20.0;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.violationManager = new ViolationManager(this);
        this.checkManager = new CheckManager(this);

        // Register events
        getServer().getPluginManager().registerEvents(new AntiCheatListener(this), this);

        // Register commands
        LacCommand lacCommand = new LacCommand(this);
        if (getCommand("lac") != null) {
            getCommand("lac").setExecutor(lacCommand);
            getCommand("lac").setTabCompleter(lacCommand);
        }

        // Start TPS calculation & violation decay task (runs every second)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                double[] tpsArr = getServer().getTPS();
                if (tpsArr != null && tpsArr.length > 0) {
                    currentTps = Math.min(20.0, Math.max(0.0, tpsArr[0]));
                }
                violationManager.decay();
            } catch (Exception ignored) {}
        }, 20L, 20L);

        getLogger().info("LarpingAntiCheat v" + getDescription().getVersion() + " successfully enabled (Paper 1.21.11 / Java 21).");
    }

    @Override
    public void onDisable() {
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

    public double tps() {
        return currentTps;
    }

    public void exempt(Player player, long ticks) {
        data(player).exempt(ticks);
    }
}
