package dev.jade;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

final class Settings {
    static final Path root = FabricLoader.getInstance().getConfigDir().resolve("jade-music");
    static final Path json = root.resolve("locations.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    String directory = root.resolve("music").toAbsolutePath().toString();
    Map<String,String> tracks = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    int schema = 4;
    Map<String,String> islands = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static Settings load() throws IOException {
        Files.createDirectories(root);
        Settings settings = new Settings();
        boolean migrate = false;
        boolean addDefaults = true;
        boolean first = !Files.exists(json);
        if (!first) {
            try (var reader = Files.newBufferedReader(json)) {
                JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
                settings.directory = data.get("directory").getAsString();
                if (!Path.of(settings.directory).isAbsolute()) throw new IOException("directory must be absolute");
                addDefaults = !data.has("schema") || data.get("schema").getAsInt() < 4;
                migrate = !data.has("islands");
                if (!migrate) for (var e : data.getAsJsonObject("islands").entrySet())
                    settings.islands.put(e.getKey(),e.getValue().getAsString());
                for (var e : data.getAsJsonObject("tracks").entrySet()) settings.tracks.put(e.getKey(),e.getValue().getAsString());
            }
        }
        Files.createDirectories(root.resolve("music"));
        if (migrate) LegacyMusic.remove(settings);
        if (addDefaults) {
            DefaultMusic.install(settings);
            settings.save();
        }
        return settings;
    }
    Set<String> catalog() {
        var keys = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        keys.addAll(Islands.byPlace.keySet());
        keys.addAll(tracks.keySet());
        return keys;
    }
    String track(String place,boolean rift) {
        if (place==null) return "";
        return tracks.getOrDefault(place,islands.getOrDefault(Islands.of(place,rift),""));
    }
    void setIsland(String group,String file,boolean replace) throws IOException {
        if (!file.isBlank()) file(file);
        islands.put(group,file);
        if (replace) tracks.keySet().removeIf(id -> Islands.of(id,false).equals(group));
    }
    Path file(String track) throws IOException {
        if (!AudioFiles.supported(track)) throw new IOException("请选择 WAV、FLAC、MP3、Ogg Vorbis、AIFF 或 AU 文件");
        Path relative = Path.of(track);
        if (relative.isAbsolute()) {
            Path path = relative.toRealPath();
            if (!Files.isRegularFile(path)) throw new IOException("不是音乐文件：" + path);
            return path;
        }
        Path base = (relative.startsWith(DefaultMusic.FOLDER) ? root.resolve("music") : Path.of(directory)).toRealPath();
        Path candidate = base.resolve(relative);
        Path path = candidate.toRealPath();
        if (!path.startsWith(base) || !Files.isRegularFile(path)) throw new IOException("Invalid music file: "+track);
        return path;
    }
    Settings copy() {
        Settings copy = new Settings();
        copy.directory = directory;
        copy.tracks.putAll(tracks);
        copy.islands.putAll(islands);
        return copy;
    }
    void save() throws IOException {
        Files.createDirectories(root);
        Path tmp = Files.createTempFile(root,"locations-",".tmp");
        try {
            Files.writeString(tmp,gson.toJson(this));
            try { Files.move(tmp,json,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(tmp,json,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
