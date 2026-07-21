package com.mcmoddev.orespawn.documentation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.loading.FMLPaths;

/** Copies the bundled public guide beside OreSpawn's configuration on first use. */
public final class DocumentationExporter {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String RESOURCE_ROOT = "/META-INF/orespawn/docs/";
	private static final String[] FILES = {
			"README.md",
			"PLAYER_GUIDE.md",
			"DEVELOPER_GUIDE.md",
			"CONFIGURATION.md",
			"PROVIDERS.md",
			"API.md",
			"FEATURES.md",
			"TEMPLATES.md",
			"DIMENSIONS.md",
			"MIGRATION.md",
			"TROUBLESHOOTING.md",
			"AGENTS.md",
			"examples/examplemod-orespawn.json",
			"examples/orespawn-global.json",
			"examples/orespawn-world.json",
			"schemas/orespawn-global.schema.json",
			"schemas/orespawn-provider.schema.json",
			"schemas/orespawn-world.schema.json",
			"schemas/profile-common.schema.json"
	};

	private DocumentationExporter() {
	}

	public static void exportBundledGuide() {
		Path target = FMLPaths.CONFIGDIR.get().resolve("orespawn-guide");
		try {
			int exported = exportMissing(target);
			if (exported > 0) {
				LOGGER.info("Exported {} OreSpawn guide files to {}", exported, target.toAbsolutePath());
			}
		} catch (IOException e) {
			LOGGER.warn("Could not export the OreSpawn guide to {}", target.toAbsolutePath(), e);
		}
	}

	/** Writes missing files only, so pack authors may annotate their exported copy. */
	public static int exportMissing(Path targetRoot) throws IOException {
		int exported = 0;
		for (String relative : FILES) {
			Path target = targetRoot.resolve(relative);
			if (Files.exists(target)) {
				continue;
			}
			try (InputStream source = DocumentationExporter.class.getResourceAsStream(RESOURCE_ROOT + relative)) {
				if (source == null) {
					throw new IOException("Missing bundled documentation resource " + relative);
				}
				Files.createDirectories(target.getParent());
				Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
				try {
					Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
					moveIntoPlace(temporary, target);
					exported++;
				} finally {
					Files.deleteIfExists(temporary);
				}
			}
		}
		return exported;
	}

	private static void moveIntoPlace(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(source, target);
		}
	}
}
