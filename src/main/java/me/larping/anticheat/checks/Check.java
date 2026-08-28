package me.larping.anticheat.checks;

/**
 * Interface representing an observable check in LarpingAntiCheat.
 *
 * <p>All player/config access goes through {@link CheckContext}, which carries
 * the immutable config snapshot and (for movement checks) a per-tick
 * server-authoritative {@link me.larping.anticheat.physics.MovementSnapshot}.
 * Checks are modular, conservative and custom-SMP compatible.
 */
public interface Check {
    String name();

    default boolean enabled() {
        return true;
    }

    void evaluate(CheckContext context);
}
