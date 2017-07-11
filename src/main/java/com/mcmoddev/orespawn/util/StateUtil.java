package com.mcmoddev.orespawn.util;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

public class StateUtil {
	private StateUtil() {
		throw new InstantiationError("This class cannot be instantiated!");
	}
	
	public static String serializeState(IBlockState state) {
		String string = state.toString();
		string = string.substring(string.indexOf('[') + 1, string.length() - (string.endsWith("]") ? 1 : 0));
		if (string.equals(state.getBlock().getRegistryName().toString())) {
			string = "normal";
		}
		return string;
	}

	public static IBlockState deserializeState(Block block, String state) {
		for (IBlockState validState : block.getBlockState().getValidStates()) {
			String string = validState.toString();
			string = string.substring(string.indexOf('[') + 1, string.length() - (string.endsWith("]") ? 1 : 0));
			if (string.equals(block.getRegistryName().toString())) {
				string = "";
			}

			if (state.equals(string)) {
				return validState;
			}
		}

		return null;
	}
}
