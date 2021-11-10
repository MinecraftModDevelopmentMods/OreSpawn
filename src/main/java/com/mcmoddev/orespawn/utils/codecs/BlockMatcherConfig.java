package com.mcmoddev.orespawn.utils.codecs;

import com.mcmoddev.orespawn.data.BlockType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class BlockMatcherConfig {
	public static final Codec<BlockMatcherConfig> CODEC = RecordCodecBuilder.create((base) -> {
		return base.group(BlockTypeConfig.CODEC.listOf().fieldOf("blocks").forGetter((config) -> config.asTypeConfig())).apply(base, BlockMatcherConfig::new);
	});

	public final List<BlockType> blocks;

	public BlockMatcherConfig(final List<BlockTypeConfig> blocksIn) {
		this.blocks = new LinkedList<>();

		this.blocks.addAll(blocksIn.stream().map(BlockType::new).collect(Collectors.toList()));
	}

	public List<BlockTypeConfig> asTypeConfig() {
		return this.blocks.stream().map( bt -> new BlockTypeConfig(bt.getType(), bt.getName())).collect(Collectors.toList());
	}
}
