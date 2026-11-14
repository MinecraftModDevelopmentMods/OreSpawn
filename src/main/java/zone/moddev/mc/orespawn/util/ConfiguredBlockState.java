package zone.moddev.mc.orespawn.util;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.arguments.BlockStateParser;

/** Reads exact flattened block states using Minecraft's own property validation. */
public final class ConfiguredBlockState {
    private ConfiguredBlockState() { }

    public static IBlockState read(JsonObject json) {
        if (json.has("metadata") && json.get("metadata").getAsInt() != 0) {
            throw new IllegalArgumentException("Use flattened block IDs and properties, not legacy metadata");
        }
        StringBuilder text = new StringBuilder(json.get("block").getAsString());
        if (json.has("properties") && json.getAsJsonObject("properties").size() != 0) {
            text.append('[');
            boolean first = true;
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry
                    : json.getAsJsonObject("properties").entrySet()) {
                if (!first) text.append(',');
                text.append(entry.getKey()).append('=').append(entry.getValue().getAsString());
                first = false;
            }
            text.append(']');
        }
        StringReader reader = new StringReader(text.toString());
        try {
            IBlockState state = new BlockStateParser(reader, false).parse(false).getState();
            if (reader.canRead()) throw new IllegalArgumentException("Unexpected block-state suffix: " + text);
            return state;
        } catch (CommandSyntaxException ex) {
            throw new IllegalArgumentException("Invalid block state: " + text, ex);
        }
    }
}
