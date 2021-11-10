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

	public static class CommonConfig {
		public final ForgeConfigSpec.BooleanValue flattenBedrock;
		public final ForgeConfigSpec.BooleanValue retroactivelyFlattenBedrock;
		public final ForgeConfigSpec.IntValue layersOfBedrock;

		CommonConfig(ForgeConfigSpec.Builder builder) {
			builder.push("general");
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
		}
	}
}
