package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.registries.ForgeRegistries;

/** Static-registry identity bridge for the pre-RegistryKey 1.13 runtime. */
public final class WorldIds {
	static final ResourceLocation OVERWORLD = id("overworld");
	static final ResourceLocation NETHER = id("the_nether");
	static final ResourceLocation END = id("the_end");

	private WorldIds() {
	}

	public static ResourceLocation dimension(IWorld world) {
		return DimensionType.func_212678_a(world.getWorld().dimension.getType());
	}

	public static ResourceLocation dimension(WorldServer world) {
		return DimensionType.func_212678_a(world.dimension.getType());
	}

	public static ResourceLocation biome(Biome biome) {
		return ForgeRegistries.BIOMES.getKey(biome);
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation("minecraft", path);
	}
}
