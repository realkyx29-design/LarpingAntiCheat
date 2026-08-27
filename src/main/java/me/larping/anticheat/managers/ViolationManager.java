package me.larping.anticheat.managers;

import me.larping.anticheat.LarpingAntiCheat; import org.bukkit.*; import org.bukkit.entity.Player; import java.util.*; import java.util.concurrent.ConcurrentHashMap;

public final class ViolationManager {
 public record Evidence(String player,String check,String type,double amount,double confidence,int ping,double tps,Location location,String detail,long timestamp) {}
 private final LarpingAntiCheat plugin; private final Map<UUID,Map<String,Double>> vl=new ConcurrentHashMap<>();
 public ViolationManager(LarpingAntiCheat p){plugin=p;}
 public double add(Player p,String check,String type,double amount,double confidence,String detail){
  if(p.hasPermission(plugin.getConfig().getString("exempt-permission","lac.bypass"))) return 0;
  double total=vl.computeIfAbsent(p.getUniqueId(),x->new ConcurrentHashMap<>()).merge(check,amount,(a,b)->Math.min(100,a+b));
  Evidence e=new Evidence(p.getName(),check,type,amount,confidence,p.getPing(),plugin.tps(),p.getLocation(),detail,System.currentTimeMillis());
  if(plugin.getConfig().getBoolean("logging.enabled")) plugin.getLogger().info(e.toString());
  if(plugin.getConfig().getBoolean("alerts") && confidence>=.70) Bukkit.broadcast("§8[§cLAC§8] §f"+p.getName()+" §7flagged §f"+check+" §7VL: §f"+String.format("%.1f",total)+" §7Ping: §f"+p.getPing()+"ms §7Confidence: §f"+(int)(confidence*100)+"%", "lac.alerts");
  return total;
 }
 public double total(Player p){return vl.getOrDefault(p.getUniqueId(),Map.of()).values().stream().mapToDouble(Double::doubleValue).sum();}
 public Map<String,Double> get(Player p){return Map.copyOf(vl.getOrDefault(p.getUniqueId(),Map.of()));} public void clear(Player p){vl.remove(p.getUniqueId());}
 public void decay(){vl.values().forEach(m->m.replaceAll((k,v)->Math.max(0,v-plugin.getConfig().getDouble("violation-decay-per-second",.15))));}
 public void punish(Player p){if(!plugin.getConfig().getBoolean("punishments.enabled"))return; for(Map<?,?> a:plugin.getConfig().getMapList("punishments.actions")) if(total(p)>=((Number)(a.containsKey("threshold") ? a.get("threshold") : Double.MAX_VALUE)).doubleValue()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(),String.valueOf(a.get("command")).replace("%player%",p.getName()));}
}
