package zone.moddev.mc.orespawn.api;

import java.util.Optional;

import net.minecraft.util.ResourceLocation;
import net.minecraft.block.BlockState;

/** A single classified geology column returned by {@link GeologySampler}. */
public interface GeologyColumn {
	ResourceLocation dimension();
	ResourceLocation biome();
	ResourceLocation geome();
	int blockX();
	int blockZ();
	int surfaceY();
	BlockState rockAt(int y);
	Optional<GeologyFamily> familyAt(int y);
}
