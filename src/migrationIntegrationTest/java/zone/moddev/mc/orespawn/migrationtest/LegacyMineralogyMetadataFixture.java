package zone.moddev.mc.orespawn.migrationtest;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Build-only helper which labels a copied 1.12 save with its prior Mineralogy version. */
public final class LegacyMineralogyMetadataFixture {
	private LegacyMineralogyMetadataFixture() { }

	public static void main(String[] arguments) throws Exception {
		if (arguments.length != 2) {
			throw new IllegalArgumentException("Expected world directory and Mineralogy version");
		}
		Path world = Paths.get(arguments[0]);
		for (String name : new String[] { "level.dat", "level.dat_old" }) {
			Path levelDat = world.resolve(name);
			if (!Files.isRegularFile(levelDat)) continue;
			NBTTagCompound root;
			try (FileInputStream input = new FileInputStream(levelDat.toFile())) {
				root = CompressedStreamTools.readCompressed(input);
			}
			NBTTagCompound fml = root.getCompoundTag("FML");
			NBTTagList mods = fml.getTagList("ModList", 10);
			boolean found = false;
			for (int index = 0; index < mods.tagCount(); index++) {
				NBTTagCompound mod = mods.getCompoundTagAt(index);
				if (!"mineralogy".equalsIgnoreCase(mod.getString("ModId"))) continue;
				mod.setString("ModVersion", arguments[1]);
				found = true;
			}
			if (!found) {
				NBTTagCompound mineralogy = new NBTTagCompound();
				mineralogy.setString("ModId", "mineralogy");
				mineralogy.setString("ModVersion", arguments[1]);
				mods.appendTag(mineralogy);
			}
			fml.setTag("ModList", mods);
			root.setTag("FML", fml);
			try (FileOutputStream output = new FileOutputStream(levelDat.toFile())) {
				CompressedStreamTools.writeCompressed(root, output);
			}
		}
	}
}
