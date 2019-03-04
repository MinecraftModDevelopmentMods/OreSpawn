package com.mcmoddev.orespawn.util;

import com.google.common.base.Optional;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.exceptions.BadStateValueException;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;

public class StateUtil {

	private StateUtil() {
		throw new InstantiationError("This class cannot be instantiated!");
	}

	public static String serializeState(final IBlockState state) {
		String string = state.toString();
		string = string.substring(string.indexOf('[') + 1,
				string.length() - (string.endsWith("]") ? 1 : 0));

		if (string.equals(state.getBlock().getRegistryName().toString())) {
			string = "normal";
		}

		OreSpawn.LOGGER.debug("State is %s (for block %s)", string,
				state.getBlock().getRegistryName());
		return string;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static IBlockState deserializeState(final Block block, final String state) throws Exception {
		String bits[];
		if(state.contains(",")) bits = state.split(",");
		else bits = new String[] { state };

		IBlockState rv = block.getDefaultState();
		
		for(String sv : bits) {
			String kvp[] = sv.split("=");
			IProperty prop = block.getBlockState().getProperty(kvp[0]);
			if(prop != null) {
				Optional<? extends Comparable> propValue = prop.parseValue(kvp[1]);
				if(propValue.isPresent())
					rv = rv.withProperty(prop, propValue.get());
				else
					throw new BadStateValueException(String.format("%s is not a valid value for property %s", kvp[1], kvp[0]));
			} else {
				throw new BadStateValueException(String.format("%s is not a known property of %s", kvp[0], block.getRegistryName()));
			}
		}
		
		return rv;
	}
}
