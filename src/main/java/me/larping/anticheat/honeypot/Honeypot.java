package me.larping.anticheat.honeypot;

import org.bukkit.entity.Player;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Central facade for honey-pot / fake-base decoys.
 *
 * <p>Holds the standard-Bukkit {@link DecoyService} and any registered
 * {@link PacketDecoyProvider} (for full client-only fake chunks via
 * ProtocolLib/PacketEvents). Other plugins call
 * {@link #registerPacketDecoyProvider(PacketDecoyProvider)} to enable the
 * packet-level fake-base feature without Hyphon shipping a packet dependency.
 */
public final class Honeypot {

    private final DecoyService decoys;
    private final List<PacketDecoyProvider> packetProviders = new CopyOnWriteArrayList<>();

    public Honeypot(DecoyService decoys) {
        this.decoys = decoys;
    }

    public DecoyService decoys() {
        return decoys;
    }

    public void registerPacketDecoyProvider(PacketDecoyProvider provider) {
        if (provider != null && provider.isAvailable()) packetProviders.add(provider);
    }

    public void unregisterPacketDecoyProvider(PacketDecoyProvider provider) {
        packetProviders.remove(provider);
    }

    public List<PacketDecoyProvider> packetProviders() {
        return packetProviders;
    }

    public boolean hasPacketDecoys() {
        return !packetProviders.isEmpty();
    }

    /** Plant a hidden decoy for a player (packet provider first, else Bukkit). */
    public void plant(Player player) {
        try {
            if (hasPacketDecoys()) {
                packetProviders.get(0).sendFakeBase(player, player.getLocation());
                return;
            }
            decoys.spawnDecoy(player);
        } catch (Throwable ignored) { }
    }
}
