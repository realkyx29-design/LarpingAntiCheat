package me.larping.anticheat.modifiers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Global registry for {@link CustomModifierProvider}s. Custom-enchantment
 * plugins register a provider so the anti-cheat recognises their legitimate
 * speed/mining/damage/area abilities instead of flagging them.
 */
public final class CapabilityRegistry {

    private static final List<CustomModifierProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private CapabilityRegistry() { }

    public static void registerProvider(CustomModifierProvider provider) {
        if (provider != null && !PROVIDERS.contains(provider)) {
            PROVIDERS.add(provider);
        }
    }

    public static void unregisterProvider(CustomModifierProvider provider) {
        PROVIDERS.remove(provider);
    }

    public static List<CustomModifierProvider> providers() {
        return PROVIDERS;
    }
}
