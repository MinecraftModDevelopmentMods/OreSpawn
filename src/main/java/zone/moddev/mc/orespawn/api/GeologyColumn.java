package zone.moddev.mc.orespawn.api;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** A single classified geology column returned by {@link GeologySampler}. */
public interface GeologyColumn {
	ResourceLocation dimension();
	ResourceLocation biome();
	ResourceLocation geome();
	int blockX();
	int blockZ();
	int surfaceY();
	/** Configured rock choice, or Stone when no configured rock covers this height. */
	BlockState rockAt(int y);
	/** Empty where OreSpawn leaves the native generator's rock unchanged. */
	Optional<GeologyFamily> familyAt(int y);
}
