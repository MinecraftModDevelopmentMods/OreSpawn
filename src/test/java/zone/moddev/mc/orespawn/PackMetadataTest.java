package zone.moddev.mc.orespawn;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import org.junit.jupiter.api.Test;

class PackMetadataTest {
	@Test
	void modAndFixturePacksSupportTheNativeResourceAndDataFormats() throws Exception {
		try (var paths = Files.walk(Path.of("src"))) {
			for (Path path : paths.filter(file -> file.getFileName().toString().equals("pack.mcmeta")).toList()) {
				JsonObject pack = JsonParser.parseString(Files.readString(path)).getAsJsonObject()
						.getAsJsonObject("pack");
				for (PackType type : PackType.values()) {
					PackMetadataSection metadata = PackMetadataSection.forPackType(type).codec()
							.parse(JsonOps.INSTANCE, pack).getOrThrow();
					assertTrue(metadata.supportedFormats().isValueInRange(
							SharedConstants.getCurrentVersion().packVersion(type)),
							() -> path + " does not support this Minecraft " + type + " format");
				}
			}
		}
	}
}
