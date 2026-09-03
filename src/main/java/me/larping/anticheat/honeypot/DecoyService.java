package me.larping.anticheat.honeypot;

import me.larping.anticheat.LarpingAntiCheat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Honey-pot decoy ("fake base") service.
 *
 * <p>The <b>standard-Bukkit</b> half of the fake-base concept: the plugin
 * spawns invisible, non-interactive marker entities (armor stands / dropped
 * items) at locations a legitimate player cannot see, then watches whether
 * anyone reacts to them. A player who navigates toward / attacks / interacts
 * with a decoy they had no in-game way to know about is exhibiting ESP — the
 * server never told their client about the decoy's <i>meaning</i> (it's
 * invisible at a distance / underground), so only an information-leak cheat
 * could reveal it.
 *
 * <p>This intentionally does <b>not</b> require a packet library. A full
 * per-client "fake chunk" (sending chunk data that exists only for one
 * player) needs ProtocolLib/PacketEvents; that is exposed via
 * {@link PacketDecoyProvider} so your packet plugin can register real
 * client-only decoys. Everything in this class runs on plain Bukkit.
 */
public final class DecoyService {

    private final LarpingAntiCheat plugin;
    /** player -> active decoys */
    private final Map<UUID, Map<UUID, Decoy>> decoys = new ConcurrentHashMap<>();

    public DecoyService(LarpingAntiCheat plugin) {
        this.plugin = plugin;
    }

    /** A tracked decoy: the entity, where it was placed, and when. */
    public record Decoy(UUID entityId, Location location, long spawnedMs) { }

    /**
     * Spawns a hidden decoy near the player (underground / a few hundred
     * blocks away) that an ESP user would "see". Returns it, or null if the
     * environment isn't safe to spawn (e.g. missing world). Spawned on the
     * main thread via a scheduled task.
     */
    public Decoy spawnDecoy(Player player) {
        try {
            World world = player.getWorld();
            if (world == null) return null;
            Location base = player.getLocation();
            // Place it ~60 blocks away at a plausible "base" position and
            // underground/indoor so normal FOV and sound never reveal it.
            int dx = 60;
            int dz = (int) ((hashy(player.getUniqueId()) % 120) - 60);
            Location at = new Location(world, base.getX() + dx, base.getY() - 3, base.getZ() + dz);
            // Keep within world vertical bounds.
            int minY = world.getMinHeight();
            if (at.getY() < minY + 2) at.setY(minY + 2);

            final Decoy[] out = new Decoy[1];
            Runnable spawn = () -> {
                try {
                    ArmorStand stand = world.spawn(at, ArmorStand.class, st -> {
                        st.setGravity(false);
                        st.setMarker(true);
                        st.setInvisible(true);
                        st.setCustomNameVisible(false);
                        st.setCustomName("decoy_" + player.getUniqueId());
                    });
                    if (stand != null) {
                        Decoy d = new Decoy(stand.getUniqueId(), at, System.currentTimeMillis());
                        decoys.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                                .put(stand.getUniqueId(), d);
                        out[0] = d;
                    }
                } catch (Throwable t) {
                    plugin.getLogger().fine("decoy spawn failed: " + t);
                }
            };
            try {
                Bukkit.getScheduler().runTask(plugin, spawn);
            } catch (Throwable t) {
                spawn.run();
            }
            return out[0];
        } catch (Throwable t) {
            return null;
        }
    }

    /** Returns and clears any active decoys for a player (e.g. on quit). */
    public void clearFor(Player player) {
        Map<UUID, Decoy> set = decoys.remove(player.getUniqueId());
        if (set == null) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Decoy d : set.values()) {
                    World w = d.location().getWorld();
                    if (w == null) continue;
                    for (Entity e : w.getEntities()) {
                        if (e.getUniqueId().equals(d.entityId())) {
                            e.remove();
                        }
                    }
                }
            });
        } catch (Throwable ignored) { }
    }

    public Map<UUID, Decoy> decoysFor(Player player) {
        return decoys.getOrDefault(player.getUniqueId(), Map.of());
    }

    private static long hashy(UUID u) {
        return Math.floorMod(u.getLeastSignificantBits(), 1000L);
    }
}
