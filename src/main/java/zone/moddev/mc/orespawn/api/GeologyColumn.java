package zone.moddev.mc.orespawn.api;

import java.util.Optional;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/** A single classified geology column returned by {@link GeologySampler}. */
public interface GeologyColumn {
	Identifier dimension();
	Identifier biome();
	Identifier geome();
	int blockX();
	int blockZ();
	int surfaceY();
	BlockState rockAt(int y);
	Optional<GeologyFamily> familyAt(int y);
}
