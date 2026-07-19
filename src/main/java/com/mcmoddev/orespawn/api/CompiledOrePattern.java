package com.mcmoddev.orespawn.api;

/**
 * Immutable, pre-decoded ore pattern used during chunk generation.
 * Implementations must be thread-safe and must not allocate or access registries,
 * configuration files, tags, or logging from {@link #place(OrePlacementContext)}.
 */
@FunctionalInterface
public interface CompiledOrePattern {
	boolean place(OrePlacementContext context);
}
