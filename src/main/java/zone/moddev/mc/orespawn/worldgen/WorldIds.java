package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;

/** Static-registry identity bridge for the pre-RegistryKey 1.15 runtime. */
public final class WorldIds {
	static final ResourceLocation OVERWORLD = id("overworld");
	static final ResourceLocation NETHER = id("the_nether");
	static final ResourceLocation END = id("the_end");

	private WorldIds() {
	}

	public static ResourceLocation dimension(IWorld world) {
		return DimensionType.getKey(world.getWorld().dimension.getType());
	}

	public static ResourceLocation dimension(ServerWorld world) {
		return DimensionType.getKey(world.dimension.getType());
	}

	public static ResourceLocation biome(Biome biome) {
		return ForgeRegistries.BIOMES.getKey(biome);
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation("minecraft", path);
	}
}
