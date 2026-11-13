package dev.jade;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class DefaultMusic {
    static final String FOLDER = "Jade Defaults";
    private static final String RESOURCE = "/jade-defaults/";

    private static InputStream open(String name) throws IOException {
        var stream = DefaultMusic.class.getResourceAsStream(RESOURCE + name);
        if (stream == null) throw new FileNotFoundException(RESOURCE + name);
        return stream;
    }
    private static Map<String,String> manifest(String name) throws IOException {
        Map<String,String> result = new TreeMap<>();
        try (var reader = new InputStreamReader(open(name), StandardCharsets.UTF_8)) {
            for (var entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                String file = entry.getValue().getAsString();
                if (!AudioFiles.supported(file) || file.contains("/") || file.contains("\\") || file.contains(":"))
                    throw new IOException("Invalid bundled filename: " + file);
                result.put(entry.getKey(), file);
            }
        }
        return result;
    }
    static void install(Settings settings) throws IOException {
        var islands = manifest("islands.json");
        var tracks = manifest("tracks.json");
        Path folder = Settings.root.resolve("music").resolve(FOLDER);
        // The curated 0.3 profile left Galatea empty for this release to fill.
        if ("".equals(settings.islands.get("Galatea")) &&
                matchesSelection(settings,"Hub Island",islands,folder) &&
                matchesSelection(settings,"The Garden",islands,folder))
            settings.islands.remove("Galatea");
        merge(settings,settings.islands,islands,folder);
        merge(settings,settings.tracks,tracks,folder);
    }
    private static boolean matchesSelection(Settings settings,String island,Map<String,String> defaults,Path folder) {
        return sameFile(settings,settings.islands.get(island),folder.resolve(defaults.get(island)));
    }
    private static boolean sameFile(Settings settings,String current,Path bundled) {
        if (current == null || current.isBlank()) return false;
        try {
            if (!Path.of(current).getFileName().equals(bundled.getFileName())) return false;
            return MusicDownloads.matchesDefault(settings.file(current),bundled.getFileName().toString());
        } catch (IOException | InvalidPathException ex) { return false; }
    }
    private static void merge(Settings settings,Map<String,String> bindings,Map<String,String> defaults,Path folder) {
        defaults.forEach((place,name) -> {
            String old = bindings.get(place);
            if (old == null || sameFile(settings,old,folder.resolve(name)))
                bindings.put(place,FOLDER + "/" + name);
        });
    }
}
