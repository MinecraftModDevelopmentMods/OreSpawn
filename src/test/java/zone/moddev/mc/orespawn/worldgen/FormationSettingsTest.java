package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.worldgen.FormationSettings.Preset;

class FormationSettingsTest {
	@Test
	void stableLayerEdgeDetailUsesTheRecalibratedPresetLadder() {
		assertEdgeDetail(Preset.TINY, 48.0D, 4.0D, 1);
		assertEdgeDetail(Preset.SMALL, 64.0D, 12.0D, 2);
		assertEdgeDetail(Preset.AVERAGE, 96.0D, 24.0D, 3);
		assertEdgeDetail(Preset.LARGE, 128.0D, 48.0D, 4);
		assertEdgeDetail(Preset.HUGE, 192.0D, 96.0D, 5);
	}

	@Test
	void customAndRecommendedEdgeDefaultsMatchAverage() {
		assertEdgeDetail(Preset.CUSTOM, 96.0D, 24.0D, 3);

		JsonObject custom = WorldGeologyProfile.recommended(false).toFormationJson()
				.getAsJsonObject("custom");
		assertEquals(96.0D, custom.get("edge_wavelength").getAsDouble());
		assertEquals(24.0D, custom.get("edge_amplitude").getAsDouble());
		assertEquals(3, custom.get("edge_octaves").getAsInt());
	}

	private static void assertEdgeDetail(Preset preset, double wavelength, double amplitude,
			int octaves) {
		assertEquals(wavelength, preset.stableEdgeWavelength);
		assertEquals(amplitude, preset.stableEdgeAmplitude);
		assertEquals(octaves, preset.stableEdgeOctaves);
	}
}
