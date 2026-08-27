package me.larping.anticheat.listeners;
import me.larping.anticheat.*; import me.larping.anticheat.data.PlayerData; import org.bukkit.*; import org.bukkit.block.Block; import org.bukkit.entity.*; import org.bukkit.event.*; import org.bukkit.event.player.*; import org.bukkit.event.entity.*; import org.bukkit.event.block.*; import org.bukkit.event.inventory.InventoryClickEvent; import org.bukkit.util.Vector;

public final class AntiCheatListener implements Listener {
 private final LarpingAntiCheat p; public AntiCheatListener(LarpingAntiCheat p){this.p=p;}
 @EventHandler public void join(PlayerJoinEvent e){p.data(e.getPlayer());} @EventHandler public void quit(PlayerQuitEvent e){p.remove(e.getPlayer());}
 @EventHandler public void teleport(PlayerTeleportEvent e){p.exempt(e.getPlayer(),30); p.data(e.getPlayer()).safe(e.getTo());}
 @EventHandler public void world(PlayerChangedWorldEvent e){p.exempt(e.getPlayer(),40);} @EventHandler public void respawn(PlayerRespawnEvent e){p.exempt(e.getPlayer(),60);}
 @EventHandler public void velocity(EntityVelocityEvent e){if(e.getEntity() instanceof Player x)p.exempt(x,12);}
 @EventHandler public void damage(EntityDamageEvent e){if(e.getEntity() instanceof Player x)p.exempt(x,8);}
 @EventHandler public void move(PlayerMoveEvent e){if(e.getTo()==null||e.getFrom().getWorld()!=e.getTo().getWorld())return; Player x=e.getPlayer(); PlayerData d=p.data(x); Location n=e.getTo(); if(d.exempt()){d.movement(n);d.safe(n);return;} if(x.isInsideVehicle()||x.isFlying()||x.getGameMode()==GameMode.SPECTATOR){d.movement(n);d.safe(n);return;} double dy=n.getY()-e.getFrom().getY(), h=Math.hypot(n.getX()-e.getFrom().getX(),n.getZ()-e.getFrom().getZ()); boolean liquid=n.getBlock().isLiquid(); boolean special=liquid||x.isGliding()||x.isClimbing()||isMechanic(e.getFrom().getBlock())||isMechanic(n.getBlock()); double limit=p.getConfig().getDouble("checks.speed.max-horizontal-per-tick",.72); limit+=Math.max(0,x.getWalkSpeed()-.2)*1.5; if(p.getConfig().getBoolean("ping-compensation")&&x.getPing()>150)limit*=1.15; if(p.tps()<18)limit*=1.15; if(h>limit&&!special&&!x.isGliding()) {int b=d.speedBuffer(d.speedBuffer()+1); if(b>=p.getConfig().getInt("checks.speed.min-confirmations",4))flag(x,"Speed A",Math.min(.8,(h-limit)*2),.82,"horizontal="+String.format("%.3f",h)+", limit="+limit);} else d.speedBuffer(d.speedBuffer()-1); if(p.getConfig().getBoolean("checks.fly.enabled",true)&&d.airTicks()>p.getConfig().getInt("checks.fly.air-ticks",35)&&Math.abs(dy)<.003&&!liquid&&!special&&!x.isGliding()){int b=d.flyBuffer(d.flyBuffer()+1);if(b>=p.getConfig().getInt("checks.fly.min-confirmations",5))flag(x,"Fly A",.5,.80,"airTicks="+d.airTicks());}else d.flyBuffer(d.flyBuffer()-1); if(dy>1.05&&!special&&!e.getFrom().getBlock().isBlockPowered())flag(x,"Jump A",.2,.72,"deltaY="+dy); d.movement(n); if(x.isOnGround())d.safe(n); }
 private boolean isMechanic(Block b){String t=b.getType().name();return t.contains("SLIME")||t.contains("HONEY")||t.contains("PISTON")||t.contains("ICE")||t.contains("LADDER")||t.contains("SCAFFOLDING")||t.contains("BUBBLE");}
 private void flag(Player x,String check,double amount,double confidence,String detail){if(!p.getConfig().getBoolean("checks.movement",true))return; double vl=p.violations().add(x,check,"movement",amount*p.getConfig().getDouble("checks.sensitivity",1),confidence,detail); if(vl>=p.getConfig().getDouble("setbacks.threshold",8)&&p.getConfig().getBoolean("setbacks.enabled",false))x.teleport(p.data(x).safe());p.punish(x);}
 @EventHandler public void interact(PlayerInteractEvent e){
  Player x=e.getPlayer(); if(e.getAction().name().contains("BLOCK")&&x.getInventory().getItemInMainHand().getType().isBlock()){
   p.data(x).action(); int rate=p.data(x).placementsPerSecond();
   if(e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && rate>p.getConfig().getInt("checks.scaffold.min-placements",6)+10 && p.getConfig().getBoolean("checks.interaction",true))
    p.violations().add(x,"FastPlace A","interaction",.25,.70,"placementsPerSecond="+rate);
  }
 }
 @EventHandler public void animation(PlayerAnimationEvent e){
  Player x=e.getPlayer(); if(!p.getConfig().getBoolean("checks.combat",true))return;
  int cps=p.data(x).clicksPerSecond(); int max=p.getConfig().getInt("checks.autoclicker.max-cps",18);
  if(cps>max && p.data(x).clicksPerSecond()>p.getConfig().getInt("checks.autoclicker.min-samples",40)/2)
   p.violations().add(x,"AutoClicker A","combat",.15,.65,"animationRate="+cps);
 }
 @EventHandler public void attack(EntityDamageByEntityEvent e){if(!(e.getDamager() instanceof Player x))return; if(!(e.getEntity() instanceof LivingEntity))return; if(x.getGameMode()==GameMode.SPECTATOR||x.isInsideVehicle())return; double dist=x.getLocation().distance(e.getEntity().getLocation()); if(p.getConfig().getBoolean("checks.reach.enabled",true)&&p.getConfig().getBoolean("checks.combat",true)&&dist>p.getConfig().getDouble("checks.reach.max-distance",3.45)+(x.getPing()>150?.25:0)){double vl=p.violations().add(x,"Reach A","combat",.8,.76,"distance="+String.format("%.2f",dist));p.punish(x);} }
}
