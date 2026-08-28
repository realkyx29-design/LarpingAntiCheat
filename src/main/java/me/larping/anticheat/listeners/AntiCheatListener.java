package me.larping.anticheat.listeners;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.data.PlayerData;
import me.larping.anticheat.managers.CheckManager;
import me.larping.anticheat.physics.MovementSnapshot;
import me.larping.anticheat.util.CollisionUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

/**
 * Central event router for all checks.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Movement checks run at {@code NORMAL} (not {@code HIGHEST}) after the
 *       server has updated the player position, with the collision-based
 *       ground state computed once per event and shared by all checks.</li>
 *   <li>Look-only move packets (no position change) skip all movement checks
 *       — the previous handler evaluated everything on every look packet,
 *       wasting most of the per-tick budget.</li>
 *   <li>{@code EntityDamageEvent} no longer grants a blanket movement bypass
 *       (standing in fire used to make every check exempt forever). Knockback
 *       is instead captured precisely and compensated.</li>
 *   <li>Block placement/break checks use the dedicated block events rather
 *       than generic interact events, so eating/bow/shield don't count.</li>
 * </ul>
 */
public final class AntiCheatListener implements Listener {

    private final LarpingAntiCheat plugin;
    private final CheckManager checks;

    public AntiCheatListener(LarpingAntiCheat plugin) {
        this.plugin = plugin;
        this.checks = plugin.checkManager();
    }

    // ================================================================
    // Lifecycle
    // ================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerData data = plugin.data(event.getPlayer());
        data.setJoinGrace(plugin.configManager().get().joinGraceMs());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.remove(event.getPlayer());
    }

    // ================================================================
    // Teleport / world / respawn — legitimate position discontinuities
    // ================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerData data = plugin.data(event.getPlayer());
        data.setTeleportGrace(plugin.configManager().get().teleportGraceMs());
        Location to = event.getTo();
        if (to != null) {
            data.rebase(to);
            data.safeLocation(to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        PlayerData data = plugin.data(event.getPlayer());
        data.setWorldChangeGrace(plugin.configManager().get().worldChangeGraceMs());
        data.rebase(event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerData data = plugin.data(event.getPlayer());
        data.setRespawnGrace(plugin.configManager().get().respawnGraceMs());
        data.rebase(event.getRespawnLocation());
        data.safeLocation(event.getRespawnLocation());
    }

    // ================================================================
    // Velocity / knockback — recorded precisely, no blanket bypass
    // ================================================================

    // Mark when a player took damage so the subsequent velocity event can be
    // attributed to knockback (combat, explosions, projectiles) rather than a
    // self-propelled velocity (pistons, riptide, launchers). Knockback is
    // applied within a tick of the damage event.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageVelocity(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim) {
            plugin.data(victim).markDamaged();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data(player);
        Vector v = event.getVelocity();

        // Ignore tiny / zero vectors so minor events don't mask cheats.
        double horizontal = Math.hypot(v.getX(), v.getZ());
        if (horizontal <= 0.06 && Math.abs(v.getY()) <= 0.1) return;

        // Velocity right after damage is combat/explosion knockback, which the
        // NoKnockback check validates. Other velocity (launchers, pistons) is
        // tracked for speed/fly compensation only.
        if (data.wasDamagedRecently()) {
            data.applyKnockback(v.getX(), v.getY(), v.getZ());
        } else {
            data.applyVelocity(v.getX(), v.getY(), v.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        plugin.data(event.getPlayer()).setRiptideGrace(700L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        // Server-authoritative flight toggles (donor flight) get a short
        // re-grace window; this does NOT exempt client-side flight hacks.
        if (event.isFlying()) {
            plugin.data(event.getPlayer()).setTeleportGrace(400L);
        }
    }

    // ================================================================
    // Movement
    // ================================================================

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        Player player = event.getPlayer();
        GameMode gm = player.getGameMode();
        if (gm == GameMode.SPECTATOR) return;

        PlayerData data = plugin.data(player);

        boolean positionChanged = from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ();

        // Compute server-authoritative ground state once; shared by all checks.
        boolean serverGround = CollisionUtil.isOnGround(to);

        // Record rotation (for killaura snap detection).
        float oldYaw = data.lastYaw();
        data.updateRotation(to.getYaw(), to.getPitch());

        if (!positionChanged) {
            // Look-only packet: still worth feeding the timer packet counter.
            CheckContext ctx = new CheckContext(plugin, player, data);
            if (passes(player)) {
                checks.timer().evaluate(ctx);
            }
            return;
        }

        data.updateMovement(to, serverGround);

        // Update safe location whenever the player is genuinely grounded and
        // not in a hard grace window; safeLocation() itself validates world.
        if (serverGround && !data.inHardGrace()) {
            data.safeLocation(to);
        }

        // Build the authoritative physics snapshot ONCE; every movement check
        // reasons from this verified state (no client flags, no duplicate
        // block/collision lookups).
        MovementSnapshot snap = MovementSnapshot.capture(player, data, data.prevLocation(), to);
        CheckContext ctx = new CheckContext(plugin, player, data, snap);
        if (!passes(player)) return;

        // Feed killaura rotation snaps (cheap).
        checks.killAura().recordRotation(player, ctx, oldYaw, to.getYaw());

        // Movement checks in cheapest-first order.
        checks.timer().evaluate(ctx);
        checks.get("blink").evaluate(ctx);
        checks.get("speed").evaluate(ctx);
        checks.get("noslow").evaluate(ctx);
        checks.get("fly").evaluate(ctx);
        checks.get("step").evaluate(ctx);
        checks.get("spider").evaluate(ctx);
        checks.get("jesus").evaluate(ctx);
        checks.get("groundspoof").evaluate(ctx);
        checks.get("phase").evaluate(ctx);
        checks.get("noknockback").evaluate(ctx);
    }

    /** Quick gate before building/evaluating checks: creative/dead/bypass handled here. */
    private boolean passes(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return false;
        if (player.isDead() || !player.isValid()) return false;
        if (player.hasPermission(plugin.configManager().get().exemptPermission())) return false;
        return true;
    }

    // ================================================================
    // Combat
    // ================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        PlayerData data = plugin.data(attacker);
        data.recordAttack(target.getUniqueId());

        CheckContext ctx = new CheckContext(plugin, attacker, data);
        checks.reach().evaluateAttack(attacker, target, ctx);
        checks.killAura().evaluateAttack(attacker, target, ctx);
        checks.killAura().evaluateSnapOnAttack(ctx);
        checks.killAura().evaluate(ctx);
    }

    // ================================================================
    // Blocks
    // ================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data(player);
        data.recordPlacement();
        CheckContext ctx = new CheckContext(plugin, player, data);
        checks.scaffold().evaluatePlace(player, event.getBlockPlaced(), ctx);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        CheckContext ctx = new CheckContext(plugin, player, plugin.data(player));
        checks.fastBreak().recordBreakStart(player, event.getBlock(), ctx);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data(player);
        CheckContext ctx = new CheckContext(plugin, player, data);
        checks.fastBreak().evaluateBreak(player, event.getBlock(), ctx);
        checks.nuker().evaluateBreak(player, event.getBlock(), ctx);
    }

    // Firework boost while gliding extends fly/grace and glide speed envelope.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFireworkBoost(org.bukkit.event.player.PlayerInteractEvent event) {
        // Cheap approximation: a gliding player using a firework star item.
        if (event.getMaterial() == Material.FIREWORK_ROCKET) {
            Player p = event.getPlayer();
            if (p.isGliding()) plugin.data(p).setGlideFireworkBoost(1500L);
        }
    }

}
