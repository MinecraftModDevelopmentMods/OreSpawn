package zone.moddev.mc.orespawn.api;

import java.util.Random;

import net.minecraftforge.fluids.Fluid;

/**
 * Allocation-free view supplied to a compiled ore pattern for one attempt.
 * OreSpawn 4.1 contexts also implement {@link OreGenerationContext}; this base
 * interface remains unchanged for binary compatibility with existing patterns.
 */
public interface OrePlacementContext {
	Random random();

	int originX();
	int originY();
	int originZ();
	int minY();
	int maxY();
	int quantity();
	int spread();
	int verticalSpread();
	int nodeSize();

	/**
	 * Returns whether this attempt may inspect or replace the position. Forge 1.10
	 * limits both ordinary generation and retrogen to the current chunk. A large
	 * deterministic pattern can still render the intersecting slice independently in
	 * every chunk.
	 */
	boolean inside(int x, int y, int z);
	boolean isFluid(int x, int y, int z, Fluid fluid);
	boolean tryPlace(int x, int y, int z);
}
