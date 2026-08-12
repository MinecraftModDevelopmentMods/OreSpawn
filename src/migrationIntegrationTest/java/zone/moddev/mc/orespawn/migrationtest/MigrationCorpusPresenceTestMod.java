package zone.moddev.mc.orespawn.migrationtest;

import net.minecraftforge.fml.common.Mod;

/**
 * Build-only identity placeholder for worlds created by the sealed corpus
 * generator. It satisfies Forge's saved-mod audit without rerunning that
 * generator or allowing it to stop the qualification server.
 */
@Mod(modid = "orespawnmigrationcorpus", name = "OreSpawn Migration Corpus Placeholder",
		version = "1.0.0", acceptedMinecraftVersions = "[1.12.2]")
public final class MigrationCorpusPresenceTestMod {
}
