package me.larping.anticheat.modifiers;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Pluggable provider for <b>custom</b> item/enchantment abilities (from your
 * custom-enchantment plugin). Registered providers are consulted when building
 * {@link Capabilities}; they can add speed/jump/mining/damage bonuses and area-
 * mining radii for items the vanilla API cannot describe.
 *
 * <p>Two ways to integrate:
 * <ol>
 *   <li>Implement this interface and call
 *       {@link CapabilityRegistry#registerProvider(CustomModifierProvider)} from
 *       your enchantment plugin (or any plugin on the server).</li>
 *   <li>Do nothing — the built-in {@link GenericCustomEnchantScanner} already
 *       recognises common speed/jump/haste/mining-ability enchant names on the
 *       held item and armour, so most setups work with zero configuration.</li>
 * </ol>
 */
@FunctionalInterface
public interface CustomModifierProvider {

    /**
     * Apply any custom, legitimate modifiers for the player's current equipment.
     * Called on the main thread for movement/combat/mining events.
     *
     * @param player       the acting player
     * @param mainHand     the player's main-hand item (may be null/air)
     * @param context      what the capabilities are being built for
     * @param caps         the capabilities to mutate (add bonuses here)
     */
    void apply(Player player, ItemStack mainHand, Context context, Capabilities caps);

    /** What the capabilities will be used for. */
    enum Context { MOVEMENT, COMBAT, MINING, GENERAL }
}
