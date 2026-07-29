package zone.moddev.mc.orespawn.init;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.worldgen.OreSpawnBiomeModifier;
import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers OreSpawn's data-driven biome modifier codec. */
public final class BiomeModifiers {
	private static final DeferredRegister<MapCodec<? extends BiomeModifier>> MODIFIERS =
			DeferredRegister.create(NeoForgeRegistries.BIOME_MODIFIER_SERIALIZERS,
					OreSpawn.MODID);

	static {
		MODIFIERS.register("runtime_worldgen", () -> OreSpawnBiomeModifier.CODEC);
	}

	private BiomeModifiers() {
	}

	public static void register(IEventBus bus) {
		MODIFIERS.register(bus);
	}
}
