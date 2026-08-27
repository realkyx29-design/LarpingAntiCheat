package me.larping.anticheat;

import me.larping.anticheat.data.PlayerData; import me.larping.anticheat.managers.ViolationManager; import me.larping.anticheat.listeners.AntiCheatListener; import me.larping.anticheat.commands.LacCommand; import org.bukkit.*; import org.bukkit.entity.Player; import org.bukkit.plugin.java.JavaPlugin; import java.util.*; import java.util.concurrent.ConcurrentHashMap;

public final class LarpingAntiCheat extends JavaPlugin {
 private final Map<UUID,PlayerData> data=new ConcurrentHashMap<>(); private ViolationManager violations; private double tps=20;
 @Override public void onEnable(){saveDefaultConfig(); violations=new ViolationManager(this); getServer().getPluginManager().registerEvents(new AntiCheatListener(this),this); LacCommand command=new LacCommand(this); getCommand("lac").setExecutor(command); getCommand("lac").setTabCompleter(command); getServer().getScheduler().runTaskTimer(this,()->{tps=Math.min(20, tps*.8+Math.min(20, getServer().getTPS()[0])*.2); violations.decay();},20,20); getLogger().info("Enabled conservative evidence-based checks.");}
 @Override public void onDisable(){data.clear();} public PlayerData data(Player p){return data.computeIfAbsent(p.getUniqueId(),x->new PlayerData(p));} public void remove(Player p){data.remove(p.getUniqueId());violations.clear(p);} public ViolationManager violations(){return violations;} public double tps(){return tps;}
 public void exempt(Player p,long ticks){data(p).exempt(ticks);} public void punish(Player p){violations.punish(p);}
}
