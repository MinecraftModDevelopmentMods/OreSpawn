package com.mcmoddev.orespawn.util;

import com.google.common.base.Optional;
import com.mcmoddev.orespawn.api.exceptions.BadStateValueException;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;

/** Deprecated metadata-state parser used by published OS3 integrations. */
@Deprecated
public final class StateUtil {
	private StateUtil() { throw new InstantiationError("This class cannot be instantiated"); }

	public static String serializeState(IBlockState state) {
		String value = state.toString();
		int start = value.indexOf('[');
		return start < 0 ? "normal" : value.substring(start + 1, value.endsWith("]") ? value.length() - 1 : value.length());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static IBlockState deserializeState(Block block, String serialized) throws BadStateValueException {
		if (serialized == null || serialized.isEmpty() || "normal".equals(serialized)) return block.getDefaultState();
		IBlockState state = block.getDefaultState();
		for (String assignment : serialized.split(",")) {
			String[] parts = assignment.trim().split("=", 2);
			if (parts.length != 2) throw new BadStateValueException("Malformed block state: " + assignment);
			IProperty property = block.getBlockState().getProperty(parts[0]);
			if (property == null) throw new BadStateValueException(parts[0] + " is not a known property of " + block.getRegistryName());
			Optional<? extends Comparable> value = property.parseValue(parts[1]);
			if (!value.isPresent()) throw new BadStateValueException(parts[1] + " is not valid for " + parts[0]);
			state = state.withProperty(property, value.get());
		}
		return state;
	}
}
