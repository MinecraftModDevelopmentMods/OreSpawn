package zone.moddev.mc.orespawn.api;

import java.util.Optional;

import net.minecraft.util.ResourceLocation;
import net.minecraft.block.state.IBlockState;

/** A single classified geology column returned by {@link GeologySampler}. */
public interface GeologyColumn {
	ResourceLocation dimension();
	ResourceLocation biome();
	ResourceLocation geome();
	int blockX();
	int blockZ();
	int surfaceY();
	IBlockState rockAt(int y);
	Optional<GeologyFamily> familyAt(int y);
	/** Predicted natural formation here, excluding later ore and player changes. */
	default Optional<FormationSample> formationAt(int y) { return Optional.empty(); }
}
