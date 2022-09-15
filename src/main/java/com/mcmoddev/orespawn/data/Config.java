package com.mcmoddev.orespawn.data;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {
	static
	{
		final Pair<CommonConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
		COMMON_SPEC = specPair.getRight();
		COMMON = specPair.getLeft();
	}

	public static final ForgeConfigSpec COMMON_SPEC;
	public static final CommonConfig COMMON;

	public static void refresh() {
	}

	public static class CommonConfig {
		public final ForgeConfigSpec.BooleanValue replaceVanillaOreGeneration;
		public final ForgeConfigSpec.BooleanValue replaceAllGeneration;
		public final ForgeConfigSpec.BooleanValue enableRetrogeneration;
		public final ForgeConfigSpec.BooleanValue forceEnableRetrogeneration;
		public final ForgeConfigSpec.BooleanValue flattenBedrock;
		public final ForgeConfigSpec.BooleanValue retroactivelyFlattenBedrock;
		public final ForgeConfigSpec.IntValue layersOfBedrock;
		public final ForgeConfigSpec.BooleanValue extractToDisk;
		public final ForgeConfigSpec.BooleanValue ignoreResources;
		public final ForgeConfigSpec.BooleanValue ignoreDisk;

		CommonConfig(ForgeConfigSpec.Builder builder) {
			builder.push("general");
			replaceVanillaOreGeneration = builder
				.comment("Attempt to override vanilla Minecraft ore generation when TRUE")
				.translation("text.mmd_orespawn.config.replace_vanilla")
				.define("Replace Vanilla Oregen", false);
			replaceAllGeneration = builder
				.comment("Attempt to replace all ore generation, even from other mods, when TRUE")
				.translation("text.mmd_orespawn.config.replace_all")
				.define("Replace All Generation", false);
			enableRetrogeneration = builder
				.comment("Attempt to generate new spawns in chunks that were previously generated or were generated with different configuration options when TRUE")
				.translation("text.mmd_orespawn.config.retrogen")
				.define("Retrogen", false);
			forceEnableRetrogeneration = builder
				.comment("Force retroactive generation of new spawns, even if the feature is configured to not perform it when TRUE")
				.translation("text.mmd_orespawn.config.force_retrogen")
				.define("Force Retrogen", false);
			flattenBedrock = builder
				.comment("Make the bedrock flat in chunks generated when this option is TRUE")
				.translation("text.mmd_orespawn.config.flatten_bedrock")
				.define("Flatten Bedrock", true);
			layersOfBedrock = builder
				.comment("How many layers of Bedrock should there be at the bottom of the world? (default 1, max 4)")
				.translation("text.mmd_orespawn.config.bedrock_layers")
				.defineInRange("Bedrock Thickness", 1, 1, 4);
			retroactivelyFlattenBedrock = builder
				.comment("Attempt flatten the bedrock in chunks generated before this option and the \"Flatten Bedrock\" option were set to TRUE")
				.translation("text.mmd_orespawn.config.retro_bedrock")
				.define("Retrogen Flat Bedrock", false);
			extractToDisk = builder
				.comment("Extract all integration configurations found to \"config/mmd-orespawn-4\" when TRUE")
				.translation("text.mmd_orespawn.config.extract_integration")
				.define("Extract Files", false);
			ignoreResources = builder
				.comment("Do not attempt to locate or use any integration configurations when TRUE")
				.translation("text.mmd_orespawn.config.ignore_integration")
				.define("Ignore Integration", false);
			ignoreDisk = builder
				.comment("Do not attempt to load any config files that are on disk when TRUE")
				.translation("text.mmd_orespawn.config.ignore_disk")
				.define("Ignore Config Files On Disk", false);
		}
	}
}
