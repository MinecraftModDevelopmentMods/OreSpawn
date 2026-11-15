package zone.moddev.mc.orespawn.commands.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.TreeSet;

/** Small, verified sidecars; never edits a Minecraft region or profile file. */
public final class DiagnosticFiles {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private DiagnosticFiles() { }

    public static String fingerprint(JsonElement value) {
        return hash(canonical(value).getBytes(StandardCharsets.UTF_8));
    }

    private static String canonical(JsonElement value) {
        if (value.isJsonObject()) {
            StringBuilder result = new StringBuilder("{");
            TreeSet<String> keys = new TreeSet<>();
            value.getAsJsonObject().entrySet().forEach(e -> keys.add(e.getKey()));
            for (String key : keys) result.append(JSON.toJson(key)).append(':')
                    .append(canonical(value.getAsJsonObject().get(key))).append(',');
            return result.append('}').toString();
        }
        if (value.isJsonArray()) {
            StringBuilder result = new StringBuilder("[");
            for (JsonElement item : value.getAsJsonArray()) result.append(canonical(item)).append(',');
            return result.append(']').toString();
        }
        return value.toString();
    }

    public static String hash(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder text = new StringBuilder();
            for (byte item : digest) text.append(String.format(Locale.ROOT, "%02x", item & 255));
            return text.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static Path child(Path root, String id) {
        if (!id.matches("[a-zA-Z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid report or backup ID");
        Path result = root.resolve(id).normalize();
        if (!result.getParent().equals(root.normalize())) throw new IllegalArgumentException("Invalid path");
        return result;
    }

    public static void write(Path target, JsonObject value) throws IOException {
        writeText(target, JSON.toJson(value) + "\n");
    }

    public static void writeText(Path target, String value) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "diagnostic-", ".tmp");
        try {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 32 * 1024 * 1024) throw new IOException("Diagnostic file exceeds 32 MiB; reduce the survey area");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (!java.util.Arrays.equals(bytes, Files.readAllBytes(temporary))) throw new IOException("Write verification failed");
            // A backup is never replaced. Lack of atomic moves is a hard failure.
            if (Files.exists(target)) throw new IOException("File already exists: " + target.getFileName());
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }

    public static JsonObject read(Path path) throws IOException {
        if (Files.size(path) > 32L * 1024 * 1024) throw new IOException("Diagnostic file is too large");
        try { return new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException e) { throw new IOException("Invalid diagnostic file", e); }
    }

    public static void backup(Path target, JsonObject payload) throws IOException {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("format", 1);
        envelope.addProperty("sha256", fingerprint(payload));
        envelope.add("payload", payload);
        write(target, envelope);
        if (!fingerprint(readBackup(target)).equals(fingerprint(payload))) throw new IOException("Backup verification failed");
    }

    public static JsonObject readBackup(Path target) throws IOException {
        JsonObject envelope = read(target);
        try {
            JsonObject payload = envelope.getAsJsonObject("payload");
            if (envelope.get("format").getAsInt() != 1
                    || !fingerprint(payload).equals(envelope.get("sha256").getAsString())) throw new IOException("Backup checksum mismatch");
            return payload;
        } catch (RuntimeException e) { throw new IOException("Invalid backup envelope", e); }
    }
}
