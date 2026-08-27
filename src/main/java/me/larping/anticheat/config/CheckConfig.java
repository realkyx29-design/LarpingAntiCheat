package me.larping.anticheat.config;

/**
 * Immutable, pre-parsed configuration for a single check.
 * Snapshot is built once on enable/reload so the hot movement path never
 * touches the YAML map (a {@code HashMap} lookup + boxing per value, ×9 checks
 * per move event was a measurable source of lag).
 */
public record CheckConfig(
        String name,
        boolean enabled,
        double sensitivity,
        double alertThreshold,
        double setbackThreshold,
        double punishmentThreshold,
        double v1,   // check-specific numeric setting (e.g. max speed)
        double v2,   // check-specific numeric setting (e.g. min confirmations)
        double v3    // check-specific numeric setting (spare)
) {
    public boolean passesAlert(double vl) {
        return vl >= alertThreshold;
    }
}
