package me.larping.anticheat.data;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class PlayerData {
  private final Player player; private Location last, safe; private long exemptUntil, lastAction;
  private double vx, vy, vz; private int airTicks, clicks, placements; private long clickWindow, placementWindow; private int speedBuffer, flyBuffer;
  public int clicksPerSecond(){ long now=System.currentTimeMillis(); if(now-clickWindow>=1000){clicks=0;clickWindow=now;} return ++clicks; }
  public int speedBuffer(){return speedBuffer;} public int flyBuffer(){return flyBuffer;}
  public int speedBuffer(int v){return speedBuffer=Math.max(0,v);} public int flyBuffer(int v){return flyBuffer=Math.max(0,v);}
  public int placementsPerSecond(){long now=System.currentTimeMillis(); if(now-placementWindow>=1000){placements=0;placementWindow=now;} return ++placements;}
  public PlayerData(Player p){player=p; last=p.getLocation().clone(); safe=last.clone();}
  public Player player(){return player;} public Location last(){return last;} public Location safe(){return safe;}
  public void movement(Location now){vx=now.getX()-last.getX(); vy=now.getY()-last.getY(); vz=now.getZ()-last.getZ(); if(now.getY()<=last.getY()+.01) airTicks=0; else airTicks++; last=now.clone();}
  public void safe(Location l){safe=l.clone();} public double horizontal(){return Math.hypot(vx,vz);} public double vy(){return vy;} public int airTicks(){return airTicks;}
  public void exempt(long ticks){exemptUntil=Math.max(exemptUntil,System.currentTimeMillis()+ticks*50);}
  public boolean exempt(){return System.currentTimeMillis()<exemptUntil;} public void action(){lastAction=System.currentTimeMillis();} public long lastAction(){return lastAction;}
}
