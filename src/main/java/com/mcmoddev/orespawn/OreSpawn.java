package com.mcmoddev.orespawn;

import com.mcmoddev.orespawn.features.NormalCloud;
import com.mcmoddev.orespawn.features.VeinFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(OreSpawn.MODID)
public class OreSpawn
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "mmdorespawn";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Feature<?>> FEATURE = DeferredRegister.create(BuiltInRegistries.FEATURE, MODID);
    public static final DeferredHolder<Feature<?>, Feature<?>> VEIN_FEATURE = FEATURE.register("mmdos4_vein", VeinFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<?>> NORMAL_CLOUD_FEATURE = FEATURE.register("mmdos4_normal_cloud", NormalCloud::new);
    public static final DeferredHolder<Feature<?>, Feature<?>> CLUSTERS_FEATURE = FEATURE.register("mmdos4_clusters", NormalCloud::new);

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public OreSpawn(IEventBus modEventBus, ModContainer modContainer) {
        FEATURE.register(modEventBus);
    }
}
