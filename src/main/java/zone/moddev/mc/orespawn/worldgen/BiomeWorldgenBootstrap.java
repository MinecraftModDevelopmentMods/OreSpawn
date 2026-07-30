package zone.moddev.mc.orespawn.worldgen;

import com.mojang.serialization.MapCodec;
import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;

/** Internal bootstrap for biome-source codecs used by saved generator data. */
public final class BiomeWorldgenBootstrap {
	private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
			DeferredRegister.create(Registries.BIOME_SOURCE, OreSpawn.MODID);

	static {
		BIOME_SOURCES.register("profile_overlay", () -> BiomeOverlaySource.CODEC);
	}

	private BiomeWorldgenBootstrap() {
	}

	public static void register(BusGroup busGroup) {
		BIOME_SOURCES.register(busGroup);
	}
}
