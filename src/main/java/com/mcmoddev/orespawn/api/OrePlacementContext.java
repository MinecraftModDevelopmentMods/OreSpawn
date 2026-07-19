package com.mcmoddev.orespawn.api;

import java.util.Random;

import net.minecraft.world.level.material.Fluid;

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

	boolean inside(int x, int y, int z);
	boolean isFluid(int x, int y, int z, Fluid fluid);
	boolean tryPlace(int x, int y, int z);
}
