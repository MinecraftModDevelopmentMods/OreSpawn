package com.mcmoddev.orespawn.utils;

import com.mojang.datafixers.util.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;

import java.util.List;
import java.util.function.Function;

public class Codecs {
	public final Codec<BlockState> EASY_STATE = Codec.either(BlockState.CODEC, Registry.BLOCK).xmap(
		either -> either.map(Function.identity(), Block::getDefaultState),
		state -> state.equals(state.getBlock().getDefaultState()) ? Either.right(state.getBlock()) : Either.left(state)
	);

	public final Codec<List<BlockState>> BLOCKSTATE_LIST = Codec.list(EASY_STATE);
	public final Codec<List<ResourceLocation>> RESOURCELOCATION_LIST = Codec.list(ResourceLocation.CODEC);

	public final class PredicateList {
		public final Codec<PredicateList> CODEC = RecordCodecBuilder.create( (base) -> {
			return base.group(
					ResourceLocation.CODEC.fieldOf("type").forGetter((config) -> config.type),
					RESOURCELOCATION_LIST.fieldOf("data").forGetter((config) -> config.data))
				.apply(base, PredicateList::new);
		});

		public final ResourceLocation type;
		public final List<ResourceLocation> data;

		public PredicateList(ResourceLocation type, List<ResourceLocation> dataIn) {
			this.type = type;
			this.data = dataIn;
		}
	}
}
