package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Static-registry identity bridge for the pre-flattening Forge 1.12 runtime. */
public final class WorldIds {
	static final ResourceLocation OVERWORLD = id("overworld");
	static final ResourceLocation NETHER = id("the_nether");
	static final ResourceLocation END = id("the_end");

	private WorldIds() {
	}

	public static ResourceLocation dimension(World world) {
		return dimension(world.provider.getDimension());
	}

	public static ResourceLocation dimension(WorldServer world) {
		return dimension(world.provider.getDimension());
	}

	public static ResourceLocation biome(Biome biome) {
		return ForgeRegistries.BIOMES.getKey(biome);
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation("minecraft", path);
	}

	private static ResourceLocation dimension(int id) {
		if (id == 0) return OVERWORLD;
		if (id == -1) return NETHER;
		if (id == 1) return END;
		return new ResourceLocation("legacy", "dimension_" + id);
	}
}
