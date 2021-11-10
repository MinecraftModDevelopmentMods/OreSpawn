package com.mcmoddev.orespawn.data;

import com.mcmoddev.orespawn.utils.codecs.BlockTypeConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.ResourceLocation;

import java.util.Locale;

public class BlockType {
	private final BlockTypeType type;
	private final ResourceLocation blockLoc;

	public BlockType(final BlockTypeConfig conf) {
		this.type = conf.type;
		this.blockLoc = conf.name;
	}

	public BlockTypeType getType() {
		return this.type;
	}

	public ResourceLocation getName() {
		return this.blockLoc;
	}

	public enum BlockTypeType {
		TAG("TAG"), BLOCK("BLOCK");


		public static final Codec<BlockTypeType> CODEC = Codec.STRING.comapFlatMap(BlockTypeType::myValueOf, BlockTypeType::asString).stable();

		private final String text;

		/**
		 * @param text
		 */
		BlockTypeType(final String text) {
			this.text = text;
		}

		public static DataResult<BlockTypeType> myValueOf(final String value) {
			final String lookup = value.toUpperCase(Locale.US);
			DataResult<BlockTypeType> res;

			switch(lookup) {
				case "TAG":
					res = DataResult.success(TAG);
					break;
				case "BLOCK":
					res = DataResult.success(BLOCK);
					break;
				default:
					res = DataResult.error(String.format("Value %s is not valid for a type flag here", lookup));
			}
			return res;
		}

		/* (non-Javadoc)
		 * @see java.lang.Enum#toString()
		 */
		@Override
		public String toString() {
			return text;
		}

		public static String asString( BlockTypeType val ) {
			return val.toString();
		}
	}
}
