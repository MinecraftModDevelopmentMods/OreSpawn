package zone.moddev.mc.orespawn.worldgen;

import java.util.LinkedHashMap;
import java.util.Map;

import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

public final class GeologyBlockAliases {
	private static final String[] MATCHING_VANILLA_STONES = new String[] {
			"andesite",
			"basalt",
			"diorite",
			"granite",
			"tuff"
	};
	private static final Map<Identifier, Identifier> DEFAULT_ALIASES = createDefaultAliases();

	private GeologyBlockAliases() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	public static Map<Identifier, Identifier> defaultAliases() {
		return new LinkedHashMap<Identifier, Identifier>(DEFAULT_ALIASES);
	}

	public static BlockState aliasState(BlockState original) {
		Identifier id = ForgeRegistries.BLOCKS.getKey(original.getBlock());
		return aliasState(id, original, null);
	}

	public static BlockState aliasState(Identifier sourceId, BlockState original,
			Map<Identifier, Identifier> configuredAliases) {
		if (sourceId == null) {
			return original;
		}

		Identifier targetId = null;
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
		return target.defaultBlockState();
	}

	private static Map<Identifier, Identifier> createDefaultAliases() {
		Map<Identifier, Identifier> aliases = new LinkedHashMap<Identifier, Identifier>();
		for (String name : MATCHING_VANILLA_STONES) {
			aliases.put(Identifier.fromNamespaceAndPath(OreSpawn.MODID, name), Identifier.fromNamespaceAndPath("minecraft", name));
		}
		return aliases;
	}
}
