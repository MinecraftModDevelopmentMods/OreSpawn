package com.mcmoddev.orespawn.api.os3;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public interface IReplacementBuilder {

    /**
     *
     * @param entryName
     * @return
     */
    IReplacementBuilder setFromName(String entryName);

    /**
     *
     * @param name
     * @return
     */
    IReplacementBuilder setName(String name);

    /**
     *
     * @param blockState
     * @return
     */
    IReplacementBuilder addEntry(IBlockState blockState);

    /**
     *
     * @param blockName
     * @return
     */
    IReplacementBuilder addEntry(String blockName);

    /**
     *
     * @param blockName
     * @param state
     * @return
     */
    IReplacementBuilder addEntry(String blockName, String state);

    /**
     *
     * @param blockName
     * @param metadata
     * @return
     * @deprecated
     */
    @Deprecated
    IReplacementBuilder addEntry(String blockName, int metadata);

    /**
     *
     * @param blockResourceLocation
     * @return
     */
    IReplacementBuilder addEntry(ResourceLocation blockResourceLocation);

    /**
     *
     * @param blockResourceLocation
     * @param state
     * @return
     */
    IReplacementBuilder addEntry(ResourceLocation blockResourceLocation, String state);

    /**
     *
     * @param blockResourceLocation
     * @param metadata
     * @return
     * @deprecated
     */
    @Deprecated
    IReplacementBuilder addEntry(ResourceLocation blockResourceLocation, int metadata);

    boolean hasEntries();

    IReplacementEntry create();
}
