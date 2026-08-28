package me.larping.anticheat.listeners;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.data.PlayerData;
import me.larping.anticheat.managers.CheckManager;
import me.larping.anticheat.modifiers.Capabilities;
import me.larping.anticheat.modifiers.CustomModifierProvider;
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
        // Spectator and creative players move with full server authority
        // (creative flight, noclip, fast break) — never run movement checks.
        if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) return;

        PlayerData data = plugin.data(player);

        // Build the legitimate-capabilities snapshot once (effects/armour/
        // enchants/custom items) so every movement check honours it.
        Capabilities caps = plugin.capabilities().analyze(player, CustomModifierProvider.Context.MOVEMENT);
        data.setCapabilities(caps);

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
        checks.boatFly().evaluate(ctx);

        // --- Server-authoritative movement enforcement ---------------
        // If a high-confidence, sustained movement check fired (blink /
        // phase / fly / speed …), reject this packet by snapping back to the
        // previous server-valid position. setTo fully rewinds the move and
        // preserves the player's look vector; it is rate-limited and only
        // ever triggered well past alert thresholds, so legitimate rubber-
        // banding cannot occur.
        var enCfg = plugin.configManager().get();
        if (!data.inHardGrace() && enCfg.enforceCorrectMovement()
                && plugin.violations().shouldCorrectMovement(player)) {
            Location revert = data.prevLocation();
            if (revert != null && revert.getWorld() != null
                    && revert.getWorld().equals(to.getWorld())) {
                Location corrected = revert.clone();
                corrected.setYaw(to.getYaw());
                corrected.setPitch(to.getPitch());
                event.setTo(corrected);
                // Cancel accumulated velocity so the illegal momentum dies.
                player.setVelocity(new Vector(0, 0, 0));
            }
        }
    }

    /** Quick gate before building/evaluating checks: creative/dead/bypass handled here. */
    private boolean passes(Player player) {
        // Dynamic OP whitelist — OPs are never processed by checks.
        try { if (player.isOp()) return false; } catch (Throwable ignored) { }
        if (player.getGameMode() == GameMode.CREATIVE) return false;
        if (player.isDead() || !player.isValid()) return false;
        if (player.hasPermission(plugin.configManager().get().exemptPermission())) return false;
        if (player.hasPermission("hyphon.bypass") || player.hasPermission("lac.bypass")) return false;
        return true;
    }

    // ================================================================
    // Combat
    // ================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (attacker.getGameMode() == GameMode.CREATIVE) return;

        PlayerData data = plugin.data(attacker);
        data.recordAttack(target.getUniqueId());

        // Capabilities for combat: max legitimate damage from the held sword
        // (attribute + sharpness + strength + recognised custom enchants).
        Capabilities caps = plugin.capabilities().analyze(attacker, CustomModifierProvider.Context.COMBAT);
        data.setCapabilities(caps);

        CheckContext ctx = new CheckContext(plugin, attacker, data);
        checks.reach().evaluateAttack(attacker, target, ctx);
        checks.killAura().evaluateAttack(attacker, target, ctx);
        checks.killAura().evaluateSnapOnAttack(ctx);
        checks.killAura().evaluate(ctx);

        // --- Server-authoritative enforcement -----------------------------
        if (plugin.configManager().get().enforceCancelAttacks()) {
            // Reach/aim violations: the illegal hit is cancelled entirely.
            if (plugin.violations().shouldCancelEvent(attacker, "Reach", "KillAura")) {
                event.setCancelled(true);
            } else if (checks.weaponDamage() != null) {
                // Damage beyond what the real held weapon can produce: reported
                // and, once sustained, cancelled — never a hard-coded value.
                checks.weaponDamage().evaluateAttack(attacker, event, caps, ctx);
            }
        }
    }

    // ================================================================
    // Blocks
    // ================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        PlayerData data = plugin.data(player);
        data.recordPlacement();
        CheckContext ctx = new CheckContext(plugin, player, data);
        checks.scaffold().evaluatePlace(player, event.getBlockPlaced(), ctx);

        // Automation validation for special blocks.
        Material placedType = event.getBlockPlaced() != null
                ? event.getBlockPlaced().getType() : null;
        if (placedType == Material.COBWEB) {
            checks.autoWeb().evaluateWebPlacement(player, event.getBlockPlaced(), ctx);
        } else if (placedType == Material.END_CRYSTAL) {
            // Crystal placed on obsidian for combat — AutoCrystal place signal.
            checks.autoCrystal().recordCrystalPlace(ctx,
                    event.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5));
        }

        // Illegal placements (out-of-reach / far too fast) are denied.
        if (plugin.configManager().get().enforceCancelBlocks()
                && plugin.violations().shouldCancelEvent(player, "Scaffold", "AutoWeb", "AutoCrystal")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        PlayerData data = plugin.data(player);
        Capabilities caps = plugin.capabilities().analyze(player, CustomModifierProvider.Context.MINING);
        data.setCapabilities(caps);
        CheckContext ctx = new CheckContext(plugin, player, data);
        checks.fastBreak().recordBreakStart(player, event.getBlock(), caps, ctx);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        PlayerData data = plugin.data(player);

        // Mining capabilities: the player's real pickaxe, Efficiency, Haste,
        // and recognised area-mining custom enchants.
        Capabilities caps = plugin.capabilities().analyze(player, CustomModifierProvider.Context.MINING);
        data.setCapabilities(caps);
        CheckContext ctx = new CheckContext(plugin, player, data);

        checks.fastBreak().evaluateBreak(player, event, caps, ctx);
        checks.nuker().evaluateBreak(player, event, caps, ctx);

        // Out-of-reach / genuinely-too-fast breaks are denied server-side.
        // Legitimate area-mining (custom pickaxe 3x3/4x4) is recognised in
        // the checks via caps.areaMineRadius and never reaches the violation.
        if (plugin.configManager().get().enforceCancelBreaks()
                && plugin.violations().shouldCancelEvent(player, "FastBreak", "Nuker")) {
            event.setCancelled(true);
        }
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
