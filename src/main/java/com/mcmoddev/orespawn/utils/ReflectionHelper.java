package com.mcmoddev.orespawn.utils;

import com.mcmoddev.orespawn.OreSpawn;
//import com.mcmoddev.orespawn.api.OS4Feature;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ReflectionHelper {
	public class OS4Feature {};
	public static Class<? extends OS4Feature> getFeatureNamed(final String name, final String fqcn) {
		Class<?> resultBase;
		Class<? extends OS4Feature> result = null;

		try {
			resultBase = Class.forName(fqcn);
			if (resultBase.isAssignableFrom(OS4Feature.class)) result = (Class<? extends OS4Feature>) resultBase;
		} catch (ClassNotFoundException e) {
			OreSpawn.LOGGER.error("Error in getting reference to class {} for feature {} -- {}", fqcn, name, e.getMessage());
			e.printStackTrace();
			return null;
		}

		return result;
	}

	public static Constructor getConstructorForFeature(Class<? extends OS4Feature> clazz) {
		Constructor constructor = null;
		try {
			constructor = clazz.getConstructor();
		} catch (NoSuchMethodException e) {
			OreSpawn.LOGGER.error("Unable to get constructor for {} - {}", clazz.getCanonicalName(), e.getMessage());
			e.printStackTrace();
			return null;
		}

		return constructor;
	}

	public static OS4Feature getFeatureClassInstance(Constructor<OS4Feature> featureClassConstructor) {
		try {
			return featureClassConstructor.newInstance();
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			OreSpawn.LOGGER.error("Could not instantiate an instance of {} -- {}", featureClassConstructor.getClass().getCanonicalName(), e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	public static OS4Feature getFeatureFromName(final String featureClassName, final String featureName) {
		Class<? extends OS4Feature> baseClass = getFeatureNamed(featureName, featureClassName);
		Constructor<OS4Feature> classConstructor;
		OS4Feature instance;
		if (baseClass != null) classConstructor = getConstructorForFeature(baseClass);
		else return null;
		if (classConstructor != null) instance = getFeatureClassInstance(classConstructor);
		else return null;

		return instance;
	}
}
