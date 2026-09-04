package com.mcmoddev.mineralogy;

/**
 * Test-only ABI bridge for the one configuration value read by the exact
 * Mineralogy 5.4.0 Geology bytecode. The published configuration class cannot
 * link on Minecraft 26.2 because several Minecraft and NeoForge types changed;
 * the geology implementation itself is loaded unchanged from the sealed jar.
 */
public final class MineralogyConfig {
    private static int geomLayerThickness = 1;

    private MineralogyConfig() {
    }

    public static int geomLayerThickness() {
        return geomLayerThickness;
    }
}
