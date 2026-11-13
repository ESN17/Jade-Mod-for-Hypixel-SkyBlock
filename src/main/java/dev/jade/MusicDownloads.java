package dev.jade;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import org.slf4j.LoggerFactory;

final class MusicDownloads {
    private static final URI SITE = URI.create("https://sapphire.esn17cabin.club/jade/audio/");
    private record Asset(String sha256,long bytes) {}
    private static final Map<String,Asset> assets = loadIndex();
    private static final Set<String> waiting = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static volatile String status = "默认曲库尚未检查";
    private static Thread worker;

    private static Map<String,Asset> loadIndex() {
        try (var input = MusicDownloads.class.getResourceAsStream("/jade-defaults/files.json");
             var reader = new InputStreamReader(Objects.requireNonNull(input),StandardCharsets.UTF_8)) {
            Map<String,Asset> result = new Gson().fromJson(reader,new TypeToken<Map<String,Asset>>() {});
            return Collections.unmodifiableMap(new TreeMap<>(result));
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
    static boolean matchesDefault(Path path,String name) throws IOException {
        Asset asset = assets.get(name);
        if (asset == null || Files.size(path) != asset.bytes) return false;
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            try (var in = new DigestInputStream(Files.newInputStream(path),hash)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(hash.digest()).equals(asset.sha256);
        } catch (NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
    }
    static boolean pending(String binding) {
        String prefix = DefaultMusic.FOLDER + "/";
        return binding.startsWith(prefix) && waiting.contains(binding.substring(prefix.length()));
    }
    static synchronized void start() {
        if (worker != null && worker.isAlive()) return;
        // Existing files remain playable while the background worker checks the cache.
        Path folder = Settings.root.resolve("music").resolve(DefaultMusic.FOLDER);
        for (String name : assets.keySet())
            if (!Files.isRegularFile(folder.resolve(name))) waiting.add(name);
        worker = Thread.ofVirtual().name("Jade default music").start(() -> sync(folder));
    }
    private static void sync(Path folder) {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
        var remaining = new LinkedHashSet<>(assets.keySet());
        try (client) {
            for (int attempt = 0; attempt < 3; attempt++) {
                if (attempt > 0) Thread.sleep(5000L * attempt);
                Iterator<String> it = remaining.iterator();
                while (it.hasNext()) {
                    String name = it.next();
                    status = "默认曲库 " + (assets.size()-remaining.size()) + "/" + assets.size() + " · " + name;
                    try {
                        Files.createDirectories(folder);
                        Path target = folder.resolve(name);
                        if (!Files.isRegularFile(target) || !matchesDefault(target,name)) {
                            waiting.add(name);
                            download(client,SITE,folder,name);
                        }
                        waiting.remove(name);
                        it.remove();
                    } catch (IOException ex) {
                        status = "默认曲库下载暂缓 · " + name + " · " + ex.getMessage();
                        LoggerFactory.getLogger("Jade").warn(status);
                        // Avoid issuing 23 connection attempts when the site is offline.
                        if (ex instanceof java.net.ConnectException || ex instanceof java.net.http.HttpTimeoutException) break;
                    }
                }
                if (remaining.isEmpty()) { status = "默认曲库已就绪（" + assets.size() + " 首）"; return; }
            }
            status = "默认曲库尚有 " + remaining.size() + " 首待同步，可用 /jade reload 重试";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            status = "默认曲库同步已暂停，可用 /jade reload 重试";
        }
    }
    static void download(HttpClient client,URI base,Path folder,String name) throws IOException,InterruptedException {
        var asset = Objects.requireNonNull(assets.get(name));
        var request = HttpRequest.newBuilder(base.resolve(asset.sha256)).timeout(Duration.ofMinutes(4)).GET().build();
        Path temp = Files.createTempFile(folder,"jade-download-", ".part");
        try {
            var response = client.send(request,HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode()!=200) throw new IOException("HTTP " + response.statusCode());
            if (!matchesDefault(temp,name)) throw new IOException("文件长度或 SHA-256 不匹配");
            Path target = folder.resolve(name);
            try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
