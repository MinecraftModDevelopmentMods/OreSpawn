package com.mcmoddev.orespawn.utils.codecs;

import com.mcmoddev.orespawn.data.BlockType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ResourceLocation;

public class BlockTypeConfig {
	public static final Codec<BlockTypeConfig> CODEC = RecordCodecBuilder.create((base) -> {
		return base.group(BlockType.BlockTypeType.CODEC.fieldOf("type").forGetter((config) -> config.type),
			ResourceLocation.CODEC.fieldOf("name").forGetter((config) -> config.name)).apply(base, BlockTypeConfig::new);
	});

	public final BlockType.BlockTypeType type;
	public final ResourceLocation name;

	public BlockTypeConfig(final BlockType.BlockTypeType refType, final ResourceLocation refID) {
		this.type = refType;
		this.name = refID;
	}
}
