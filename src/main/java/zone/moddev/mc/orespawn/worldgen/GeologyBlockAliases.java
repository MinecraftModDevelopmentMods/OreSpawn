package zone.moddev.mc.orespawn.worldgen;

import java.util.LinkedHashMap;
import java.util.Map;

import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.util.ResourceLocation;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.registries.ForgeRegistries;

public final class GeologyBlockAliases {
	private static final String[] MATCHING_VANILLA_STONES = new String[] {
			"andesite",
			"basalt",
			"diorite",
			"granite",
			"tuff"
	};
	private static final Map<ResourceLocation, ResourceLocation> DEFAULT_ALIASES = createDefaultAliases();

	private GeologyBlockAliases() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	public static Map<ResourceLocation, ResourceLocation> defaultAliases() {
		return new LinkedHashMap<ResourceLocation, ResourceLocation>(DEFAULT_ALIASES);
	}

	public static IBlockState aliasState(IBlockState original) {
		ResourceLocation id = ForgeRegistries.BLOCKS.getKey(original.getBlock());
		return aliasState(id, original, null);
	}

	public static IBlockState aliasState(ResourceLocation sourceId, IBlockState original,
			Map<ResourceLocation, ResourceLocation> configuredAliases) {
		if (sourceId == null) {
			return original;
		}

		ResourceLocation targetId = null;
		targetId = configuredAliases == null
				? DEFAULT_ALIASES.get(sourceId)
				: configuredAliases.get(sourceId);

		if (targetId == null || targetId.equals(sourceId)) {
			return original;
		}

		Block target = ForgeRegistries.BLOCKS.getValue(targetId);
		if (target == null || target == Blocks.AIR) {
			return original;
		}
		return target.getDefaultState();
	}

	private static Map<ResourceLocation, ResourceLocation> createDefaultAliases() {
		Map<ResourceLocation, ResourceLocation> aliases = new LinkedHashMap<ResourceLocation, ResourceLocation>();
		for (String name : MATCHING_VANILLA_STONES) {
			aliases.put(new ResourceLocation(OreSpawn.MODID, name), new ResourceLocation("minecraft", name));
		}
		return aliases;
	}
}
