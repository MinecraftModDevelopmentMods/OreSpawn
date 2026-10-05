package zone.moddev.mc.orespawn.api;

import java.util.Random;

import net.minecraft.fluid.Fluid;

/** Allocation-free view supplied to a compiled ore pattern for one attempt. */
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
	 * Returns whether this attempt may inspect or replace the position.
	 * Initial generation can use Minecraft's loaded, writable region to cross chunk borders.
	 * Retrogen is limited to the chunk being updated.
	 */
	boolean inside(int x, int y, int z);
	boolean isFluid(int x, int y, int z, Fluid fluid);
	boolean tryPlace(int x, int y, int z);
}
