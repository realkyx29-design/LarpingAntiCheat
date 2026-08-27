package me.larping.anticheat.listeners;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.checks.combat.ReachCheck;
import me.larping.anticheat.checks.combat.RotationCheck;
import me.larping.anticheat.checks.movement.FlyCheck;
import me.larping.anticheat.checks.movement.FreecamCheck;
import me.larping.anticheat.checks.movement.GroundSpoofCheck;
import me.larping.anticheat.checks.movement.PhaseCheck;
import me.larping.anticheat.checks.movement.SpeedCheck;
import me.larping.anticheat.checks.movement.TimerCheck;
import me.larping.anticheat.checks.world.ScaffoldCheck;
import me.larping.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityVelocityEvent;
import org.bukkit.event.player.*;

public final class AntiCheatListener implements Listener {
    private final LarpingAntiCheat plugin;
    private final SpeedCheck speedCheck = new SpeedCheck();
    private final FlyCheck flyCheck = new FlyCheck();
    private final ReachCheck reachCheck = new ReachCheck();
    private final ScaffoldCheck scaffoldCheck = new ScaffoldCheck();
    private final TimerCheck timerCheck = new TimerCheck();
    private final GroundSpoofCheck groundSpoofCheck = new GroundSpoofCheck();
    private final PhaseCheck phaseCheck = new PhaseCheck();
    private final RotationCheck rotationCheck = new RotationCheck();
    private final FreecamCheck freecamCheck = new FreecamCheck();

    public AntiCheatListener(LarpingAntiCheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        plugin.data(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data(player);
        long graceMs = plugin.getConfig().getLong("grace-periods.teleport", 1000);
        data.setTeleportGrace(graceMs);
        if (event.getTo() != null) {
            data.safeLocation(event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data(player);
        long graceMs = plugin.getConfig().getLong("grace-periods.world-change", 1000);
        data.setWorldChangeGrace(graceMs);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data(player);
        long graceMs = plugin.getConfig().getLong("grace-periods.respawn", 1500);
        data.setRespawnGrace(graceMs);
        data.safeLocation(event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(EntityVelocityEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerData data = plugin.data(player);
            long graceMs = plugin.getConfig().getLong("grace-periods.velocity", 500);
            data.setVelocityGrace(graceMs);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerData data = plugin.data(player);
            long graceMs = plugin.getConfig().getLong("grace-periods.damage", 500);
            data.setDamageGrace(graceMs);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        PlayerData data = plugin.data(player);
        data.updateMovement(to);

        CheckContext context = new CheckContext(plugin, player, data);

        // Evaluate cheat client protection checks
        timerCheck.evaluate(player, context);
        speedCheck.evaluate(player, context);
        flyCheck.evaluate(player, context);
        groundSpoofCheck.evaluate(player, context);
        phaseCheck.evaluate(player, context);
        rotationCheck.evaluate(player, context);
        freecamCheck.evaluate(player, context);

        if (player.isOnGround() && !data.isGraceful()) {
            data.safeLocation(to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            Player player = event.getPlayer();
            PlayerData data = plugin.data(player);
            data.recordPlacement();

            CheckContext context = new CheckContext(plugin, player, data);
            scaffoldCheck.evaluate(player, context);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data(player);
        data.recordPlacement();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            PlayerData data = plugin.data(attacker);
            CheckContext context = new CheckContext(plugin, attacker, data);
            rotationCheck.evaluate(attacker, context);

            if (event.getEntity() instanceof LivingEntity target) {
                reachCheck.evaluateAttack(attacker, target, context);
            }
        }
    }
}
