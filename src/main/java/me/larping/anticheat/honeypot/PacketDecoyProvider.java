package me.larping.anticheat.honeypot;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Optional integration hook for the full "fake chunk / fake base" honey-pot.
 *
 * <p>True per-client fake chunks — sending chunk or entity data that exists
 * only for one player and is stripped from every other player — cannot be
 * done with the stable Bukkit API. It requires a packet layer
 * (ProtocolLib or PacketEvents). Rather than ship a hard dependency the
 * project can't compile/verify, Hyphon exposes this interface: your packet
 * plugin (or an addon) implements it and registers via
 * {@link me.larping.anticheat.honeypot.Honeypot#registerPacketDecoyProvider}.
 *
 * <p>When registered, the ESP/decoy check asks the provider to send a
 * hidden fake base at a chosen location; if that player then paths toward /
 * interacts with the (to them invisible) decoy, it is recorded as ESP
 * evidence. When no provider is present, the standard Bukkit
 * {@link DecoyService} entities are used instead.
 */
public interface PacketDecoyProvider {

    /**
     * Send a client-only fake base/entity to {@code player} near {@code near}.
     * Implementations should make it invisible/intangible to every other
     * player and not present on the server world.
     *
     * @return a token identifying the sent decoy (used to later remove it).
     */
    Object sendFakeBase(Player player, Location near);

    /** Remove a previously sent client-only decoy by the token returned above. */
    void removeFakeBase(Player player, Object token);

    /** Whether the implementation is available on this server. */
    boolean isAvailable();
}
