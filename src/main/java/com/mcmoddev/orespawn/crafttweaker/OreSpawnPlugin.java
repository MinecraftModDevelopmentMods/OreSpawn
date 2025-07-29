package com.mcmoddev.orespawn.crafttweaker;
import com.blamejared.crafttweaker.api.plugin.*;
import com.blamejared.crafttweaker.api.zencode.scriptrun.IScriptRunModuleConfigurator;
import com.mcmoddev.orespawn.OreSpawn;

/*
TODO: Find docs on how to actually register a loader and work out the various @ZenClass/@ZenMethod requirements for
      making things work.
 */
@CraftTweakerPlugin(OreSpawn.MODID+":crt_plugin")
public class OreSpawnPlugin implements ICraftTweakerPlugin {
    @Override
    public void registerLoaders(final ILoaderRegistrationHandler handler) {
        handler.registerLoader(CrTConstants.LOADER_NAME);
    }

    @Override
    public void registerModuleConfigurators(final IScriptRunModuleConfiguratorRegistrationHandler handler) {
        final IScriptRunModuleConfigurator defaultConfig = IScriptRunModuleConfigurator.createDefault(OreSpawn.MODID);
        handler.registerConfigurator(CrTConstants.LOADER_NAME, defaultConfig);
    }

    @Override
    public void manageJavaNativeIntegration(final IJavaNativeIntegrationRegistrationHandler handler) {
/*
        this.zenGatherer.listProviders();
        this.zenGatherer.onCandidates(candidate -> this.zenClassRegistrationManager.attemptRegistration(candidate.loader(), candidate.clazz(), handler));
        this.zenClassRegistrationManager.attemptDeferredRegistration(handler); */
    }

    @Override
    public void registerLoadSource(final IScriptLoadSourceRegistrationHandler handler) {
/*
        handler.registerLoadSource(CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID);
        handler.registerLoadSource(CraftTweakerConstants.CLIENT_RECIPES_UPDATED_SOURCE_ID);*/
    }

    // last step, load scripts:
    /*
                final ScriptRunConfiguration configuration = new ScriptRunConfiguration(
                    CraftTweakerConstants.TAGS_LOADER_NAME,
                    CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID, // TODO("Custom load source?")
                    ScriptRunConfiguration.RunKind.EXECUTE
            );

            try {
                CraftTweakerAPI.getScriptRunManager()
                        .createScriptRun(configuration)
                        .execute();
            } catch(final Throwable e) {
                CraftTweakerCommon.logger().error("Unable to run tag scripts due to an error", e);
            }
     */
    @Override
    public void initialize() {}
}
