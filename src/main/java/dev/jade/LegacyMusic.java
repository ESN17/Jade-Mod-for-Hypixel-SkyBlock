package dev.jade;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

final class LegacyMusic {
    // Only delete files matching a factory recording in the original managed folder.
    static void remove(Settings settings) throws IOException {
        Properties hashes = new Properties();
        try (var in=LegacyMusic.class.getResourceAsStream("/legacy-audio.properties")) { hashes.load(in); }
        Path managed=Settings.root.resolve("music").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(managed)) return;
        Set<Path> removed=new HashSet<>();
        for (String filename:hashes.stringPropertyNames()) {
            Path file=managed.resolve(filename);
            if (Files.exists(file,LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    var digest=MessageDigest.getInstance("SHA-256");
                    try(var in=new DigestInputStream(Files.newInputStream(file),digest)) {
                        in.transferTo(OutputStream.nullOutputStream());
                    }
                    if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(hashes.getProperty(filename))) continue;
                } catch(NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
                Files.delete(file);
            }
            removed.add(file);
        }
        settings.tracks.entrySet().removeIf(e -> {
            if (e.getValue().isEmpty()) return false;
            try {
                Path candidate=Path.of(settings.directory).resolve(e.getValue()).toAbsolutePath().normalize();
                if (removed.contains(candidate)) return true;
                // 0.2 fell back to the managed library for relative factory filenames.
                return hashes.containsKey(e.getValue()) && !Files.exists(candidate) && removed.contains(managed.resolve(e.getValue()));
            } catch(InvalidPathException ignored) { return false; }
        });
    }
}
