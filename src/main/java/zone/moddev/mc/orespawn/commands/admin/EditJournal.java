package zone.moddev.mc.orespawn.commands.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import zone.moddev.mc.orespawn.util.JsonCopies;

/** The immutable write-ahead plan is also sufficient to recover a partial edit. */
public final class EditJournal {
    public final JsonObject header;
    public final List<Cell> cells;

    public EditJournal(JsonObject header, List<Cell> cells) {
        this.header = JsonCopies.copy(header);
        this.cells = Collections.unmodifiableList(new ArrayList<>(cells));
        validate();
    }

    private void validate() {
        if (!header.has("minecraft_version") || !"1.13.2".equals(header.get("minecraft_version").getAsString()))
            throw new IllegalArgumentException("This journal is not a Minecraft 1.13.2 block snapshot");
        for (String key : new String[]{"world", "dimension", "chunk_x", "chunk_z", "profile", "operator", "snapshot"})
            if (!header.has(key)) throw new IllegalArgumentException("Incomplete edit journal: " + key);
        if (cells.size() > 65536) throw new IllegalArgumentException("Edit exceeds one 1.13 chunk");
        Set<Integer> positions = new HashSet<>();
        for (Cell cell : cells) {
            if (cell.index < 0 || cell.index >= 65536 || !positions.add(cell.index))
                throw new IllegalArgumentException("Invalid or repeated journal position");
            if (cell.before.equals(cell.after)) throw new IllegalArgumentException("Journal contains an unchanged cell");
        }
    }

    public JsonObject json() {
        JsonObject result = JsonCopies.copy(header);
        JsonArray changes = new JsonArray();
        for (Cell cell : cells) {
            JsonObject change = new JsonObject();
            change.addProperty("index", cell.index);
            change.addProperty("before", cell.before);
            change.addProperty("after", cell.after);
            changes.add(change);
        }
        result.add("changes", changes);
        return result;
    }

    public static EditJournal read(JsonObject object) {
        JsonObject header = JsonCopies.copy(object);
        header.remove("changes");
        List<Cell> cells = new ArrayList<>();
        for (JsonElement value : object.getAsJsonArray("changes")) {
            JsonObject cell = value.getAsJsonObject();
            cells.add(new Cell(cell.get("index").getAsInt(), cell.get("before").getAsString(), cell.get("after").getAsString()));
        }
        return new EditJournal(header, cells);
    }

    public static boolean canUndo(String current, Cell cell) {
        return current.equals(cell.after) || current.equals(cell.before);
    }

    public static final class Cell {
        public final int index;
        public final String before;
        public final String after;
        public Cell(int index, String before, String after) { this.index = index; this.before = before; this.after = after; }
    }
}
