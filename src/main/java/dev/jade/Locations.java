package dev.jade;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.*;

final class Locations {
    private static final Pattern FORMAT = Pattern.compile("(?i)§[0-9a-z]");
    private static final Pattern MARK = Pattern.compile("[⏣ф⧖⌛⏳\uE020\uE067]\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TEAM_PADDING = Pattern.compile("[🍫💣👽🔮🐍👾🌠🍭⚽🏀👹🎁🎉🎂🔫]");
    private static final Pattern GARDEN = Pattern.compile(
        "(?i)(?:(?:The )?Garden(?: Plot)?|花园)(?:\\s*[\\uE018\\uE07Fൠ]?\\s*[x×]\\s*\\d+)?|(?:Plot|地块)(?:[: #：-]+.*)?");
    static boolean riftMarker;
    static final Map<String,String> chinese = new HashMap<>();
    record Place(String id, String name, String region) {}
    static final List<Place> places;
    static final Map<String,List<Place>> byName = new HashMap<>();
    static {
        try (var in = new InputStreamReader(Objects.requireNonNull(
                Locations.class.getResourceAsStream("/jade-locations.json")), StandardCharsets.UTF_8)) {
            places = List.of(new Gson().fromJson(in, Place[].class));
            try (var labels = new InputStreamReader(Objects.requireNonNull(
                    Locations.class.getResourceAsStream("/jade-place-names.json")), StandardCharsets.UTF_8)) {
                for (var entry : com.google.gson.JsonParser.parseReader(labels).getAsJsonObject().entrySet())
                    chinese.put(entry.getKey(),entry.getValue().getAsString());
            }
            for (Place p : places) byName.computeIfAbsent(key(p.name()), k -> new ArrayList<>()).add(p);
        } catch (IOException ex) { throw new ExceptionInInitializerError(ex); }
    }

    static String clean(String s) {
        return TEAM_PADDING.matcher(FORMAT.matcher(s).replaceAll("")).replaceAll("")
                .replace('\u00a0',' ').replace('’','\'').strip();
    }
    static String key(String s) { return clean(s).toLowerCase(Locale.ROOT); }
    static boolean isNone(String name) { return name != null && key(name).equals("none"); }
    static String resolve(String raw, boolean rift, String island) {
        String name = clean(raw);
        if (GARDEN.matcher(name).matches()) return "The Garden";
        name=chinese.getOrDefault(name,name);
        if (name.equalsIgnoreCase("Your Island")) return "Private Island";
        if (name.matches("(?i)(The )?Catacombs(?: \\([FM]?[0-9]+\\)| Entrance Floor)?")) return "Catacombs";
        var matches = byName.get(key(name));
        if (matches == null) return name;
        if (matches.size() == 1) return matches.getFirst().id;
        for (Place p : matches) {
            if (rift && p.region.equals("Rift Dimension")) return p.id;
            if (!rift && islandMatch(island,p.region)) return p.id;
        }
        // Only use the remaining non-Rift candidate if it is unique.
        var outside = matches.stream().filter(p -> !p.region.equals("Rift Dimension")).toList();
        return outside.size() == 1 && !rift ? outside.getFirst().id : name + " (area unknown)";
    }

    private static boolean islandMatch(String island, String region) {
        return region.equalsIgnoreCase(island) || region.equals("Hub Island") && island.equalsIgnoreCase("Hub")
                || region.equals("Galatea") && Set.of("moonglade marsh","torrhus canyon").contains(key(island));
    }

    static String current(Minecraft mc) {
        riftMarker=false;
        var connection = mc.getConnection();
        if (mc.level == null || mc.player == null || connection == null || connection.getServerData() == null) return null;
        String host = key(connection.getServerData().ip).replaceFirst(":\\d+$","").replaceFirst("\\.$","");
        if (!host.equals("hypixel.net") && !host.endsWith(".hypixel.net")) return null;
        var board = mc.level.getScoreboard();
        var slot = SidebarSlot.forTeam(board.getPlayersTeam(mc.player.getScoreboardName()));
        Objective obj = slot == null ? null : board.getDisplayObjective(slot);
        if (obj == null) obj = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null || !key(obj.getDisplayName().getString()).contains("skyblock")) return null;
        boolean dungeon = false;
        boolean none = false;
        for (var entry : board.listPlayerScores(obj)) {
            if (entry.isHidden()) continue;
            String line = clean(PlayerTeam.formatNameForTeam(board.getPlayersTeam(entry.owner()), entry.ownerName()).getString());
            if (line.matches("(?i).*The Catacombs \\([FM]?[0-9]+\\).*") || line.startsWith("Dungeon Cleared:")) dungeon = true;
            Matcher m = MARK.matcher(line);
            if (!m.find()) {
                none |= isNone(line);
                continue;
            }
            String name = m.group(1).strip();
            if (isNone(name)) { none = true; continue; }
            var candidates = byName.get(key(name));
            String island = "";
            if (candidates != null && candidates.size() > 1) {
                for (var info : connection.getOnlinePlayers()) {
                    var label = info.getTabListDisplayName();
                    if (label == null) continue;
                    String tab = clean(label.getString());
                    if (tab.startsWith("Area:")) { island = tab.substring(5).strip(); break; }
                }
            }
            riftMarker="фФ⧖⌛⏳\uE020".indexOf(m.group().charAt(0))>=0 || island.equalsIgnoreCase("The Rift");
            return resolve(name,riftMarker,island);
        }
        if (none) return "None";
        return dungeon ? "Catacombs" : null;
    }
}
