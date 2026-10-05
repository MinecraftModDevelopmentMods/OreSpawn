package zone.moddev.mc.orespawn.api;

/**
 * An immutable ore pattern decoded before chunk generation starts.
 * Implementations must be thread-safe. {@link #place(OrePlacementContext)} must not allocate,
 * access registries, read configuration files or tags, or write to logs.
 */
@FunctionalInterface
public interface CompiledOrePattern {
	boolean place(OrePlacementContext context);
}
