package zone.moddev.mc.orespawn.commands.admin;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.block.Block;
import net.minecraft.state.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Registry names and properties remain meaningful when numeric IDs change. */
public final class DiagnosticStates {
    private DiagnosticStates() { }

    static java.util.function.Predicate<IBlockState> filter(String filter) {
        if (filter.isEmpty()) return state -> true;
        com.mojang.brigadier.StringReader reader = new com.mojang.brigadier.StringReader(filter);
        try {
            net.minecraft.command.arguments.BlockStateParser parsed =
                    new net.minecraft.command.arguments.BlockStateParser(reader, false).parse(false);
            if (reader.canRead()) throw new IllegalArgumentException("Unexpected block filter suffix");
            Block block = parsed.getState().getBlock();
            Map<IProperty<?>, Comparable<?>> properties = new java.util.LinkedHashMap<>(parsed.getProperties());
            return state -> {
                if (state.getBlock() != block) return false;
                for (Map.Entry<IProperty<?>, Comparable<?>> entry : properties.entrySet())
                    if (!entry.getValue().equals(state.getValues().get(entry.getKey()))) return false;
                return true;
            };
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            throw new IllegalArgumentException("Invalid block filter: " + filter, ex);
        }
    }

    public static String key(IBlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) throw new IllegalArgumentException("Unregistered block state");
        Map<String, String> properties = new TreeMap<>();
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getValues().entrySet())
            properties.put(entry.getKey().getName(), propertyName(entry.getKey(), entry.getValue()));
        StringBuilder key = new StringBuilder(id.toString()).append('[');
        properties.forEach((name, value) -> key.append(name).append('=').append(value).append(','));
        return key.append(']').toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyName(IProperty property, Comparable value) { return property.getName(value); }

    public static IBlockState resolve(String key) {
        int start = key.indexOf('[');
        if (start < 1 || !key.endsWith("]")) throw new IllegalArgumentException("Invalid block state");
        ResourceLocation id = new ResourceLocation(key.substring(0, start));
        if (!ForgeRegistries.BLOCKS.containsKey(id)) throw new IllegalArgumentException("Missing block " + id);
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        IBlockState state = block.getDefaultState();
        for (String pair : key.substring(start + 1, key.length() - 1).split(",")) {
            if (pair.isEmpty()) continue;
            String[] parts = pair.split("=", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid property");
            IProperty<?> property = block.getStateContainer().getProperty(parts[0]);
            if (property == null) throw new IllegalArgumentException("Missing property " + parts[0]);
            state = withProperty(state, property, parts[1]);
        }
        if (!key(state).equals(key)) throw new IllegalArgumentException("Incomplete or noncanonical state");
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState withProperty(IBlockState state, IProperty property, String value) {
        java.util.Optional parsed = property.parseValue(value);
        if (!parsed.isPresent()) throw new IllegalArgumentException("Invalid property value");
        return state.with(property, (Comparable) parsed.get());
    }
}
