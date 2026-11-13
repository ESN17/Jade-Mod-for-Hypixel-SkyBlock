package dev.jade;

import net.minecraft.client.Minecraft;

final class BloodRoom {
    private Object world;
    void message(Minecraft mc,String raw) {
        String s = Locations.clean(raw);
        if (s.equals("[BOSS] The Watcher: You have proven yourself. You may pass.") ||
                s.startsWith("EXTRA STATS") || s.startsWith("Dungeon starts in")) {
            world = null;
        } else if (starts(s) && "Catacombs".equals(Locations.current(mc))) world = mc.level;
    }
    static boolean starts(String s) {
        return s.equals("The BLOOD DOOR has been opened!") || s.matches("(?:\\[[^\\]]+\\] )?[A-Za-z0-9_]{1,16} opened a BLOOD door!")
                || s.startsWith("[BOSS] The Watcher:");
    }
    String select(Object level,String place) {
        if (level != world || place != null && !place.equals("Catacombs")) world = null;
        return world != null && "Catacombs".equals(place) ? Islands.BLOOD : place;
    }
}
