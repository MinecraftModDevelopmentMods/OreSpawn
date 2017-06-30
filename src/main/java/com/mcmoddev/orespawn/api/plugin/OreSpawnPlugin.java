package com.mcmoddev.orespawn.api.plugin;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(TYPE)
public @interface OreSpawnPlugin {
	// the Mod this is for - will be used for
	// generating the name of the json the config
	// will get saved to and should also be the
	// actual mod-id we can use for creating a
	// resource location
	String modid();
	
	// resource location segment to look in
	// for registered config files
	String resourcePath() default "";
}
